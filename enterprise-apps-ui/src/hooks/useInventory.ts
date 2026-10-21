import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { inventoryService } from "../services/limsService";
import type {
  InventoryFilters,
  InventoryItemDto,
  InventoryItemPayload,
  LimsResponse,
} from "../types/lims";

// ============================================================
// INVENTARIS ALAT LAB — termasuk penyesuaian stok
// Paginasi berbasis-0 mengikuti service LIMS.
// ============================================================
export const inventoryKeys = {
  all: ["lims", "inventory"] as const,
  lists: () => [...inventoryKeys.all, "list"] as const,
  list: (filters: InventoryFilters) => [...inventoryKeys.lists(), filters] as const,
  detail: (id: number | undefined) => [...inventoryKeys.all, "detail", id] as const,
  categories: () => [...inventoryKeys.all, "categories"] as const,
  dashboard: () => [...inventoryKeys.all, "dashboard"] as const,
};

// ----- LIST -----
export function useInventoryItems(filters: InventoryFilters = {}) {
  return useQuery({
    queryKey: inventoryKeys.list(filters),
    queryFn: () => inventoryService.list(filters),
    placeholderData: keepPreviousData,
  });
}

// ----- DETAIL -----
export function useInventoryItem(id: number | undefined) {
  return useQuery({
    queryKey: inventoryKeys.detail(id),
    queryFn: () => inventoryService.get(id!),
    enabled: id !== undefined && id !== null,
    select: (res) => res.data,
  });
}

// ----- KATEGORI (dropdown filter) -----
export function useInventoryCategories() {
  return useQuery({
    queryKey: inventoryKeys.categories(),
    queryFn: () => inventoryService.categories(),
    // Daftar kategori hanya berubah saat ada alat berkategori baru; 10 menit
    // cukup dan menghemat satu request tiap halaman inventaris dibuka.
    staleTime: 10 * 60_000,
    select: (res) => res.data,
  });
}

// ----- DASHBOARD -----
export function useInventoryDashboard() {
  return useQuery({
    queryKey: inventoryKeys.dashboard(),
    queryFn: () => inventoryService.getDashboardStats(),
    select: (res) => res.data,
  });
}

// ============================================================
// MUTASI CRUD
// ============================================================
export function useCreateInventoryItem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: InventoryItemPayload) => inventoryService.create(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: inventoryKeys.all });
    },
  });
}

export function useUpdateInventoryItem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: InventoryItemPayload }) =>
      inventoryService.update(id, payload),
    onSuccess: (_res, { id }) => {
      queryClient.invalidateQueries({ queryKey: inventoryKeys.detail(id) });
      queryClient.invalidateQueries({ queryKey: inventoryKeys.lists() });
      // Nilai total & hitungan stok rendah di dashboard ikut berubah.
      queryClient.invalidateQueries({ queryKey: inventoryKeys.dashboard() });
    },
  });
}

export function useDeleteInventoryItem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => inventoryService.delete(id),
    onSuccess: (_res, id) => {
      queryClient.removeQueries({ queryKey: inventoryKeys.detail(id) });
      queryClient.invalidateQueries({ queryKey: inventoryKeys.lists() });
      queryClient.invalidateQueries({ queryKey: inventoryKeys.dashboard() });
    },
  });
}

// ============================================================
// PENYESUAIAN STOK — contoh optimistic update
//
// Tidak ada endpoint khusus stok: stok adalah kolom `quantity` pada item, dan
// PUT /inventory/{id} bersifat REPLACE penuh (LimsInventoryItemService.apply()
// menimpa semua kolom tanpa cek null). Karena itu mutasi ini WAJIB mengirim
// item utuh — kirim `{ quantity }` saja akan mengosongkan nama, lokasi, dan
// tanggal kalibrasi alat tersebut.
// ============================================================

/** Buang field turunan/read-only agar cocok dengan payload yang diterima server. */
function toPayload(item: InventoryItemDto): InventoryItemPayload {
  const { id, totalValue, lowStock, createdAt, updatedAt, ...editable } = item;
  void id, totalValue, lowStock, createdAt, updatedAt;
  return editable;
}

/**
 * Cerminan rumus turunan server (LimsInventoryItemDto):
 * lowStock = quantity <= minStock, totalValue = quantity * unitPrice.
 * Dihitung ulang di klien supaya badge "Stok Rendah" tidak salah tampil
 * selama jeda menunggu respons.
 */
function applyStockDelta(item: InventoryItemDto, delta: number): InventoryItemDto {
  const quantity = Math.max(0, item.quantity + delta);
  return {
    ...item,
    quantity,
    lowStock: quantity <= item.minStock,
    totalValue: quantity * item.unitPrice,
  };
}

interface AdjustStockVars {
  /** Item versi terakhir yang dilihat user — dikirim utuh karena PUT me-replace. */
  item: InventoryItemDto;
  /** Positif untuk penerimaan barang, negatif untuk pemakaian. */
  delta: number;
}

type AdjustStockContext = {
  previousLists: [readonly unknown[], LimsResponse<InventoryItemDto[]> | undefined][];
  previousDetail: LimsResponse<InventoryItemDto> | undefined;
};

export function useAdjustStock() {
  const queryClient = useQueryClient();

  return useMutation<LimsResponse<InventoryItemDto>, Error, AdjustStockVars, AdjustStockContext>({
    mutationFn: ({ item, delta }) =>
      inventoryService.update(item.id, {
        ...toPayload(item),
        quantity: Math.max(0, item.quantity + delta),
      }),

    // Jalan SEBELUM request dikirim: cache langsung diubah supaya tombol +/-
    // terasa instan. Nilai kembaliannya menjadi `context` di onError/onSettled.
    onMutate: async ({ item, delta }) => {
      // Wajib: batalkan refetch yang sedang jalan, jika tidak respons lama
      // bisa mendarat setelah patch ini dan menimpanya kembali.
      await queryClient.cancelQueries({ queryKey: inventoryKeys.all });

      const previousLists = queryClient.getQueriesData<LimsResponse<InventoryItemDto[]>>({
        queryKey: inventoryKeys.lists(),
      });
      const previousDetail = queryClient.getQueryData<LimsResponse<InventoryItemDto>>(
        inventoryKeys.detail(item.id)
      );

      // setQueriesData (jamak) menambal SEMUA halaman/filter yang ter-cache,
      // bukan hanya yang sedang tampil.
      queryClient.setQueriesData<LimsResponse<InventoryItemDto[]>>(
        { queryKey: inventoryKeys.lists() },
        (old) =>
          old?.data
            ? {
                ...old,
                data: old.data.map((row) =>
                  row.id === item.id ? applyStockDelta(row, delta) : row
                ),
              }
            : old
      );

      queryClient.setQueryData<LimsResponse<InventoryItemDto>>(
        inventoryKeys.detail(item.id),
        (old) => (old?.data ? { ...old, data: applyStockDelta(old.data, delta) } : old)
      );

      return { previousLists, previousDetail };
    },

    // Gagal → kembalikan snapshot. Tanpa ini user melihat stok yang tidak
    // pernah tersimpan di server.
    onError: (_err, { item }, context) => {
      context?.previousLists.forEach(([key, data]) => {
        queryClient.setQueryData(key, data);
      });
      if (context?.previousDetail) {
        queryClient.setQueryData(inventoryKeys.detail(item.id), context.previousDetail);
      }
    },

    // Berhasil maupun gagal: sinkronkan ulang dengan server. Server tetap
    // pemegang kebenaran — tebakan optimistik di atas hanya untuk sementara.
    onSettled: (_res, _err, { item }) => {
      queryClient.invalidateQueries({ queryKey: inventoryKeys.detail(item.id) });
      queryClient.invalidateQueries({ queryKey: inventoryKeys.lists() });
      queryClient.invalidateQueries({ queryKey: inventoryKeys.dashboard() });
    },
  });
}
