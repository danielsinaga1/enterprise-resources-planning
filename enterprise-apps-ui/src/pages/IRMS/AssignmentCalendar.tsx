import { useState, useEffect, useRef, useCallback } from "react";
import FullCalendar from "@fullcalendar/react";
import dayGridPlugin from "@fullcalendar/daygrid";
import timeGridPlugin from "@fullcalendar/timegrid";
import interactionPlugin from "@fullcalendar/interaction";
import listPlugin from "@fullcalendar/list";
import type { DateSelectArg, EventClickArg, DatesSetArg } from "@fullcalendar/core";
import { Dialog } from "primereact/dialog";
import { Button } from "primereact/button";
import { Toast } from "primereact/toast";
import { ProgressSpinner } from "primereact/progressspinner";
import { Tag } from "primereact/tag";
import { taskAssignmentService } from "../../services";
import type { TaskAssignment } from "../../types/irms";
import TaskAssignmentModal from "./TaskAssignmentModal";

/* ============================================================
   ASSIGNMENT CALENDAR — Calendar Penugasan
   Visualize, create and manage task assignments via calendar
   ============================================================ */

const STATUS_COLORS: Record<string, string> = {
  Todo: "#6366f1",
  "In Progress": "#f59e0b",
  Done: "#22c55e",
  Cancelled: "#ef4444",
};

const PRIORITY_SEVERITY: Record<string, "danger" | "warning" | "info" | "success"> = {
  High: "danger",
  Medium: "warning",
  Low: "info",
};

const AssignmentCalendar = () => {
  const toast = useRef<Toast>(null);
  const calendarRef = useRef<FullCalendar>(null);

  // State
  const [tasks, setTasks] = useState<TaskAssignment[]>([]);
  const [loading, setLoading] = useState(false);
  const [currentRange, setCurrentRange] = useState<{ start: string; end: string } | null>(null);

  // Modal state
  const [modalVisible, setModalVisible] = useState(false);
  const [selectedTask, setSelectedTask] = useState<TaskAssignment | null>(null);
  const [defaultDates, setDefaultDates] = useState<{ start: string; end: string } | null>(null);

  // Detail view state
  const [detailVisible, setDetailVisible] = useState(false);
  const [detailTask, setDetailTask] = useState<TaskAssignment | null>(null);

  // ── Fetch tasks for visible date range ──────────────────────
  const loadTasks = useCallback(async (startDate: string, endDate: string) => {
    setLoading(true);
    try {
      const data = await taskAssignmentService.calendar(startDate, endDate);
      setTasks(data);
    } catch {
      toast.current?.show({
        severity: "error",
        summary: "Error",
        detail: "Gagal memuat data kalender",
        life: 3000,
      });
    } finally {
      setLoading(false);
    }
  }, []);

  // ── Calendar date range changes (month navigation) ──────────
  const handleDatesSet = useCallback(
    (arg: DatesSetArg) => {
      const start = arg.startStr.slice(0, 10);
      const end = arg.endStr.slice(0, 10);
      setCurrentRange({ start, end });
      loadTasks(start, end);
    },
    [loadTasks]
  );

  // ── Convert tasks → FullCalendar events ─────────────────────
  const calendarEvents = tasks.map((task) => ({
    id: String(task.id),
    title: task.title,
    start: task.startDate,
    end: addOneDay(task.endDate), // FullCalendar end is exclusive
    backgroundColor: STATUS_COLORS[task.status] ?? "#6366f1",
    borderColor: STATUS_COLORS[task.status] ?? "#6366f1",
    extendedProps: { task },
  }));

  // ── Click on a date → open create modal ─────────────────────
  const handleDateSelect = (selectInfo: DateSelectArg) => {
    setSelectedTask(null);
    setDefaultDates({
      start: selectInfo.startStr.slice(0, 10),
      end: subtractOneDay(selectInfo.endStr.slice(0, 10)),
    });
    setModalVisible(true);
  };

  // ── Click on an event → open detail dialog ──────────────────
  const handleEventClick = (clickInfo: EventClickArg) => {
    const task = clickInfo.event.extendedProps.task as TaskAssignment;
    setDetailTask(task);
    setDetailVisible(true);
  };

  // ── After save (create/update) ──────────────────────────────
  const handleSaveSuccess = () => {
    setModalVisible(false);
    setSelectedTask(null);
    setDefaultDates(null);
    toast.current?.show({
      severity: "success",
      summary: "Berhasil",
      detail: "Penugasan berhasil disimpan",
      life: 3000,
    });
    if (currentRange) loadTasks(currentRange.start, currentRange.end);
  };

  // ── Edit from detail dialog ─────────────────────────────────
  const handleEditFromDetail = () => {
    setDetailVisible(false);
    setSelectedTask(detailTask);
    setDefaultDates(null);
    setModalVisible(true);
  };

  // ── Delete ──────────────────────────────────────────────────
  const handleDelete = async () => {
    if (!detailTask) return;
    try {
      await taskAssignmentService.delete(detailTask.id);
      setDetailVisible(false);
      setDetailTask(null);
      toast.current?.show({
        severity: "success",
        summary: "Berhasil",
        detail: "Penugasan berhasil dihapus",
        life: 3000,
      });
      if (currentRange) loadTasks(currentRange.start, currentRange.end);
    } catch {
      toast.current?.show({
        severity: "error",
        summary: "Error",
        detail: "Gagal menghapus penugasan",
        life: 3000,
      });
    }
  };

  return (
    <div className="p-4">
      <Toast ref={toast} />

      {/* Header */}
      <div className="flex justify-between items-center mb-4">
        <div>
          <h2 className="text-2xl font-bold text-gray-800 m-0">
            Calendar Penugasan
          </h2>
          <p className="text-gray-500 mt-1 mb-0">
            Kelola penugasan dan daily report melalui tampilan kalender
          </p>
        </div>
        <Button
          label="Tambah Penugasan"
          icon="pi pi-plus"
          onClick={() => {
            setSelectedTask(null);
            setDefaultDates({
              start: new Date().toISOString().slice(0, 10),
              end: new Date().toISOString().slice(0, 10),
            });
            setModalVisible(true);
          }}
        />
      </div>

      {/* Legend */}
      <div className="flex gap-4 mb-4 flex-wrap">
        {Object.entries(STATUS_COLORS).map(([status, color]) => (
          <div key={status} className="flex items-center gap-2">
            <div
              className="w-3 h-3 rounded-full"
              style={{ backgroundColor: color }}
            />
            <span className="text-sm text-gray-600">{status}</span>
          </div>
        ))}
      </div>

      {/* Calendar */}
      <div className="bg-white rounded-lg shadow-sm border p-4 relative">
        {loading && (
          <div className="absolute inset-0 bg-white/60 z-10 flex items-center justify-center rounded-lg">
            <ProgressSpinner style={{ width: "40px", height: "40px" }} />
          </div>
        )}
        <FullCalendar
          ref={calendarRef}
          plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin, listPlugin]}
          initialView="dayGridMonth"
          headerToolbar={{
            left: "prev,next today",
            center: "title",
            right: "dayGridMonth,timeGridWeek,listWeek",
          }}
          locale="id"
          selectable
          selectMirror
          editable={false}
          events={calendarEvents}
          select={handleDateSelect}
          eventClick={handleEventClick}
          datesSet={handleDatesSet}
          height="auto"
          dayMaxEvents={3}
          eventDisplay="block"
          buttonText={{
            today: "Hari Ini",
            month: "Bulan",
            week: "Minggu",
            list: "Daftar",
          }}
        />
      </div>

      {/* ── Create/Edit Modal ────────────────────────────────── */}
      <TaskAssignmentModal
        visible={modalVisible}
        task={selectedTask}
        defaultDates={defaultDates}
        onHide={() => {
          setModalVisible(false);
          setSelectedTask(null);
          setDefaultDates(null);
        }}
        onSuccess={handleSaveSuccess}
        toast={toast}
      />

      {/* ── Detail Dialog ────────────────────────────────────── */}
      <Dialog
        header="Detail Penugasan"
        visible={detailVisible}
        style={{ width: "500px" }}
        modal
        onHide={() => {
          setDetailVisible(false);
          setDetailTask(null);
        }}
        footer={
          <div className="flex justify-end gap-2">
            <Button
              label="Hapus"
              icon="pi pi-trash"
              severity="danger"
              outlined
              onClick={handleDelete}
            />
            <Button
              label="Edit"
              icon="pi pi-pencil"
              onClick={handleEditFromDetail}
            />
          </div>
        }
      >
        {detailTask && (
          <div className="flex flex-col gap-3">
            <div>
              <label className="text-sm font-semibold text-gray-500">Judul</label>
              <p className="m-0 text-lg font-medium">{detailTask.title}</p>
            </div>

            {detailTask.description && (
              <div>
                <label className="text-sm font-semibold text-gray-500">Deskripsi</label>
                <p className="m-0">{detailTask.description}</p>
              </div>
            )}

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-sm font-semibold text-gray-500">Tanggal Mulai</label>
                <p className="m-0">{detailTask.startDate}</p>
              </div>
              <div>
                <label className="text-sm font-semibold text-gray-500">Tanggal Selesai</label>
                <p className="m-0">{detailTask.endDate}</p>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-sm font-semibold text-gray-500">Status</label>
                <div className="mt-1">
                  <Tag
                    value={detailTask.status}
                    style={{
                      backgroundColor: STATUS_COLORS[detailTask.status] ?? "#6366f1",
                    }}
                  />
                </div>
              </div>
              {detailTask.priority && (
                <div>
                  <label className="text-sm font-semibold text-gray-500">Prioritas</label>
                  <div className="mt-1">
                    <Tag
                      value={detailTask.priority}
                      severity={PRIORITY_SEVERITY[detailTask.priority] ?? "info"}
                    />
                  </div>
                </div>
              )}
            </div>

            {detailTask.assignee && (
              <div>
                <label className="text-sm font-semibold text-gray-500">Ditugaskan kepada</label>
                <p className="m-0">
                  {detailTask.assignee.name}
                  {detailTask.assignee.npp ? ` (${detailTask.assignee.npp})` : ""}
                </p>
              </div>
            )}

            {detailTask.dailyReportId && (
              <div>
                <label className="text-sm font-semibold text-gray-500">
                  Terkait Daily Report
                </label>
                <p className="m-0">ID: {detailTask.dailyReportId}</p>
              </div>
            )}
          </div>
        )}
      </Dialog>
    </div>
  );
};

// ── Helpers ───────────────────────────────────────────────────
function addOneDay(dateStr: string): string {
  const d = new Date(dateStr);
  d.setDate(d.getDate() + 1);
  return d.toISOString().slice(0, 10);
}

function subtractOneDay(dateStr: string): string {
  const d = new Date(dateStr);
  d.setDate(d.getDate() - 1);
  return d.toISOString().slice(0, 10);
}

export default AssignmentCalendar;
