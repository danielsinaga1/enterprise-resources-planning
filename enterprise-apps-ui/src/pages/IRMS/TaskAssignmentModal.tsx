import { useState, useEffect, type RefObject } from "react";
import { Dialog } from "primereact/dialog";
import { InputText } from "primereact/inputtext";
import { InputTextarea } from "primereact/inputtextarea";
import { Calendar } from "primereact/calendar";
import { Dropdown } from "primereact/dropdown";
import { Button } from "primereact/button";
import type { Toast } from "primereact/toast";
import { taskAssignmentService } from "../../services";
import { toLocalISODate } from "../../utils/date";
import type {
  TaskAssignment,
  AddTaskAssignmentPayload,
  UpdateTaskAssignmentPayload,
} from "../../types/irms";

interface Props {
  visible: boolean;
  task: TaskAssignment | null; // null = create, otherwise edit
  defaultDates: { start: string; end: string } | null;
  onHide: () => void;
  onSuccess: () => void;
  toast: RefObject<Toast | null>;
}

const STATUS_OPTIONS = [
  { label: "Todo", value: "Todo" },
  { label: "In Progress", value: "In Progress" },
  { label: "Done", value: "Done" },
  { label: "Cancelled", value: "Cancelled" },
];

const PRIORITY_OPTIONS = [
  { label: "Low", value: "Low" },
  { label: "Medium", value: "Medium" },
  { label: "High", value: "High" },
];

const TaskAssignmentModal = ({ visible, task, defaultDates, onHide, onSuccess, toast }: Props) => {
  const isEdit = !!task;

  // Form state
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [startDate, setStartDate] = useState<Date | null>(null);
  const [endDate, setEndDate] = useState<Date | null>(null);
  const [status, setStatus] = useState("Todo");
  const [priority, setPriority] = useState("Medium");
  const [submitting, setSubmitting] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});

  // Pre-fill form when task or defaultDates change
  useEffect(() => {
    if (task) {
      setTitle(task.title);
      setDescription(task.description ?? "");
      setStartDate(new Date(task.startDate));
      setEndDate(new Date(task.endDate));
      setStatus(task.status);
      setPriority(task.priority ?? "Medium");
    } else {
      setTitle("");
      setDescription("");
      setStatus("Todo");
      setPriority("Medium");
      if (defaultDates) {
        setStartDate(new Date(defaultDates.start));
        setEndDate(new Date(defaultDates.end));
      } else {
        setStartDate(null);
        setEndDate(null);
      }
    }
    setErrors({});
  }, [task, defaultDates, visible]);

  // ── Validation ──────────────────────────────────────────────
  const validate = (): boolean => {
    const errs: Record<string, string> = {};
    if (!title.trim()) errs.title = "Judul wajib diisi";
    if (!startDate) errs.startDate = "Tanggal mulai wajib diisi";
    if (!endDate) errs.endDate = "Tanggal selesai wajib diisi";
    if (startDate && endDate && startDate > endDate) {
      errs.endDate = "Tanggal selesai tidak boleh sebelum tanggal mulai";
    }
    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  // ── Submit ──────────────────────────────────────────────────
  const handleSubmit = async () => {
    if (!validate()) return;
    setSubmitting(true);
    try {
      const fmt = (d: Date) => toLocalISODate(d);

      if (isEdit && task) {
        const payload: UpdateTaskAssignmentPayload = {
          title,
          description: description || undefined,
          start_date: fmt(startDate!),
          end_date: fmt(endDate!),
          status,
          priority,
        };
        await taskAssignmentService.update(task.id, payload);
      } else {
        const payload: AddTaskAssignmentPayload = {
          title,
          description: description || undefined,
          start_date: fmt(startDate!),
          end_date: fmt(endDate!),
          status,
          priority,
        };
        await taskAssignmentService.create(payload);
      }
      onSuccess();
    } catch {
      toast.current?.show({
        severity: "error",
        summary: "Error",
        detail: `Gagal ${isEdit ? "memperbarui" : "membuat"} penugasan`,
        life: 3000,
      });
    } finally {
      setSubmitting(false);
    }
  };

  const dialogFooter = (
    <div className="flex justify-end gap-2">
      <Button
        label="Batal"
        icon="pi pi-times"
        severity="secondary"
        outlined
        className="border-round-lg shadow-1 font-semibold"
        style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem' }}
        onClick={onHide}
        disabled={submitting}
      />
      <Button
        label={isEdit ? "Simpan Perubahan" : "Buat Penugasan"}
        icon="pi pi-check"
        className="border-round-lg shadow-1 font-semibold"
        style={{ gap: '0.5rem', borderRadius: '10px', padding: '0.65rem 1.25rem' }}
        onClick={handleSubmit}
        loading={submitting}
      />
    </div>
  );

  return (
    <Dialog
      header={isEdit ? "Edit Penugasan" : "Buat Penugasan Baru"}
      visible={visible}
      style={{ width: "550px" }}
      modal
      onHide={onHide}
      footer={dialogFooter}
      closable={!submitting}
    >
      <div className="flex flex-col gap-4 pt-2">
        {/* Title */}
        <div className="flex flex-col gap-1">
          <label htmlFor="task-title" className="font-semibold text-sm">
            Judul <span className="text-red-500">*</span>
          </label>
          <InputText
            id="task-title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="Masukkan judul penugasan"
            className={errors.title ? "p-invalid" : ""}
          />
          {errors.title && (
            <small className="p-error">{errors.title}</small>
          )}
        </div>

        {/* Description */}
        <div className="flex flex-col gap-1">
          <label htmlFor="task-desc" className="font-semibold text-sm">
            Deskripsi
          </label>
          <InputTextarea
            id="task-desc"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={3}
            placeholder="Deskripsi tugas (opsional)"
          />
        </div>

        {/* Dates */}
        <div className="grid grid-cols-2 gap-3">
          <div className="flex flex-col gap-1">
            <label htmlFor="task-start" className="font-semibold text-sm">
              Tanggal Mulai <span className="text-red-500">*</span>
            </label>
            <Calendar
              id="task-start"
              value={startDate}
              onChange={(e) => setStartDate(e.value as Date)}
              dateFormat="yy-mm-dd"
              showIcon
              className={errors.startDate ? "p-invalid" : ""}
            />
            {errors.startDate && (
              <small className="p-error">{errors.startDate}</small>
            )}
          </div>
          <div className="flex flex-col gap-1">
            <label htmlFor="task-end" className="font-semibold text-sm">
              Tanggal Selesai <span className="text-red-500">*</span>
            </label>
            <Calendar
              id="task-end"
              value={endDate}
              onChange={(e) => setEndDate(e.value as Date)}
              dateFormat="yy-mm-dd"
              showIcon
              minDate={startDate ?? undefined}
              className={errors.endDate ? "p-invalid" : ""}
            />
            {errors.endDate && (
              <small className="p-error">{errors.endDate}</small>
            )}
          </div>
        </div>

        {/* Status & Priority */}
        <div className="grid grid-cols-2 gap-3">
          <div className="flex flex-col gap-1">
            <label htmlFor="task-status" className="font-semibold text-sm">
              Status
            </label>
            <Dropdown
              id="task-status"
              value={status}
              options={STATUS_OPTIONS}
              onChange={(e) => setStatus(e.value)}
              placeholder="Pilih status"
            />
          </div>
          <div className="flex flex-col gap-1">
            <label htmlFor="task-priority" className="font-semibold text-sm">
              Prioritas
            </label>
            <Dropdown
              id="task-priority"
              value={priority}
              options={PRIORITY_OPTIONS}
              onChange={(e) => setPriority(e.value)}
              placeholder="Pilih prioritas"
            />
          </div>
        </div>
      </div>
    </Dialog>
  );
};

export default TaskAssignmentModal;
