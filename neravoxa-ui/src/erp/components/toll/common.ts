/**
 * Jasa Maklon: tipe respons API /toll/** dan helper bersama (opsi item/gudang, format kuantitas, pesan error).
 */
import { useQuery } from '@tanstack/react-query';
import api from '@/lib/api';
import { formatNumber, toNumber } from '@/lib/format';

export type Num = string | number | null | undefined;

export const TOLL_BASE = '/toll';
export const PRICING_BASES = ['InputQty', 'OutputQty', 'Flat'] as const;
export type PricingBasis = (typeof PRICING_BASES)[number];
export const BY_PRODUCT_OWNERS = ['Customer', 'Factory'] as const;
export type ByProductOwner = (typeof BY_PRODUCT_OWNERS)[number];
export const ORDER_STATUSES = ['Open', 'InProgress', 'Completed', 'Cancelled'] as const;

export interface TollOrderRow {
  id: string;
  orderNumber: string;
  date: string;
  partyId: string;
  partyName: string;
  serviceItemId: string | null;
  serviceItemCode: string | null;
  serviceItemName: string | null;
  pricingBasis: PricingBasis;
  rate: Num;
  minimumCharge: Num;
  taxPct: Num;
  byProductOwner: ByProductOwner;
  plannedQty: Num;
  expectedYieldPct: Num;
  storageRate: Num;
  storageFreeDays: number;
  status: string;
  notes: string | null;
  completedAt: string | null;
  cancelledAt: string | null;
  receivedQty?: Num;
  processedQty?: Num;
  balanceQty?: Num;
  billedAmount?: Num;
}

export interface TollBillingLine {
  chargeType: 'Service' | 'Minimum' | 'Storage';
  itemName: string;
  quantity: Num;
  unit: string;
  rate: Num;
  amount: Num;
  description: string;
}

export interface TollBilling {
  pricingBasis: PricingBasis;
  rate: Num;
  basisQty: Num;
  serviceAmount: Num;
  billedServiceQty: Num;
  billedServiceAmount: Num;
  unbilledQty: Num;
  unbilledServiceAmount: Num;
  minimumCharge: Num;
  minimumApplies: boolean;
  billedMinimumAmount: Num;
  minimumTopUp: Num;
  storage: { rate: Num; freeDays: number; qtyDays: Num; chargeableDays: number; until: string; fee: Num; billed: Num; unbilled: Num };
  billedTotal: Num;
  unbilledTotal: Num;
  taxPct: Num;
  lines: TollBillingLine[];
}

export interface TollProduction {
  runs: number;
  inputQty: Num;
  mainOutputQty: Num;
  byProductQty: Num;
  shrinkQty: Num;
  yieldPct: Num;
  byProductPct: Num;
  shrinkPct: Num;
  expectedYieldPct: Num;
  yieldVariancePct: Num;
  inputUnit: string | null;
  outputUnit: string | null;
}

export interface TollOrderDetail extends TollOrderRow {
  serviceItemUnit: string | null;
  stock: Array<{ itemId: string; itemCode: string; itemName: string; unit: string; receivedQty: Num; processedQty: Num; producedQty: Num; returnedQty: Num; balanceQty: Num }>;
  stockByWarehouse: Array<{ itemId: string; itemName: string; unit: string; warehouseId: string; warehouseName: string; balanceQty: Num }>;
  production: TollProduction;
  factoryByProducts: Array<{ itemId: string; itemName: string; unit: string; quantity: Num; value: Num }>;
  receipts: Array<{ id: string; sttNumber: string; date: string; status: string; quantity: Num }>;
  runs: Array<{ id: string; runNumber: string; date: string; isCancelled: boolean; yieldPct: Num; inputQty: Num; outputQty: Num }>;
  returns: Array<{ id: string; returnNumber: string; date: string; status: string; quantity: Num }>;
  invoices: Array<{ id: string; invoiceNumber: string; date: string; status: string; grandTotal: Num; outstanding: Num; tollAmount: Num }>;
  billing: TollBilling;
}

export interface TollOrderOption {
  id: string;
  orderNumber: string;
  partyId: string;
  partyName: string;
  status: string;
  byProductOwner: ByProductOwner;
  serviceItemName: string | null;
  date: string;
}

export interface CustomerReturnRow {
  id: string;
  returnNumber: string;
  date: string;
  partyId: string;
  partyName: string;
  partyAddress?: string | null;
  tollOrderId: string | null;
  tollOrderNumber: string | null;
  status: string;
  nomorMobil: string | null;
  driverName: string | null;
  recipientName: string | null;
  notes: string | null;
  summary?: string | null;
  items?: Array<{ id: string; itemId: string; itemCode: string; itemName: string; quantity: Num; unit: string; warehouseId: string; warehouseName: string }>;
}

export const num = (v: Num) => toNumber(v) ?? 0;
export const qty = (v: Num) => formatNumber(num(v), { maxDecimals: 3 });

export function errorMessage(err: unknown, fallback: string): string {
  const data = (err as { response?: { data?: { error?: unknown } } })?.response?.data;
  return typeof data?.error === 'string' ? data.error : fallback;
}

export interface ItemOption {
  value: string;
  label: string;
  name: string;
  unit: string;
  code: string;
}

/** Item persediaan aktif (≤500) untuk pemilih baris. */
export function useInventoryItemOptions(enabled = true) {
  return useQuery({
    queryKey: ['inventory-items-toll'],
    queryFn: async (): Promise<ItemOption[]> => {
      const res = await api.get('/inventory/items?limit=500');
      const all = (res.data.data ?? res.data) as Array<{ id: string; code: string; name: string; unit: string; isActive?: boolean }>;
      return all
        .filter((i) => i.isActive !== false)
        .map((i) => ({ value: i.id, label: `${i.code} · ${i.name}`, name: i.name, unit: i.unit, code: i.code }));
    },
    enabled,
    staleTime: 60_000,
  });
}

/** Gudang aktif. */
export function useWarehouseOptions(enabled = true) {
  return useQuery({
    queryKey: ['warehouses-active'],
    queryFn: async () => {
      const res = await api.get('/warehouses');
      const all = (res.data.data ?? res.data) as Array<{ id: string; name: string; isActive: boolean; isDefault?: boolean }>;
      return all.filter((w) => w.isActive);
    },
    enabled,
    staleTime: 60_000,
    select: (rows) => rows.map((w) => ({ value: w.id, label: w.name, isDefault: !!w.isDefault })),
  });
}

/** Order Jasa Maklon yang bisa dipilih di dokumen (use = RECEIVE | PROCESS | RETURN). */
export function useTollOrderOptions(partyId: string | null | undefined, use: 'RECEIVE' | 'PROCESS' | 'RETURN', enabled = true) {
  return useQuery({
    queryKey: ['toll-orders', 'options', partyId ?? 'all', use],
    queryFn: async () =>
      (await api.get<TollOrderOption[]>(`${TOLL_BASE}/orders/options`, { params: { partyId: partyId || undefined, use } })).data,
    enabled,
  });
}
