import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { DataTable } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Button } from 'primereact/button';
import { Tag } from 'primereact/tag';
import { Card } from 'primereact/card';
import { BreadCrumb } from 'primereact/breadcrumb';
import { InputSwitch } from 'primereact/inputswitch';
import { Dropdown } from 'primereact/dropdown';
import { Divider } from 'primereact/divider';
import { ConfirmDialog, confirmDialog } from 'primereact/confirmdialog';

const DEMO_HISTORY = [
  { id: 1, waktu: '2025-06-25 02:00:00', tipe: 'Otomatis', ukuran: '45.2 MB', status: 'berhasil' },
  { id: 2, waktu: '2025-06-24 02:00:00', tipe: 'Otomatis', ukuran: '44.8 MB', status: 'berhasil' },
  { id: 3, waktu: '2025-06-23 15:30:00', tipe: 'Manual', ukuran: '44.5 MB', status: 'berhasil' },
  { id: 4, waktu: '2025-06-23 02:00:00', tipe: 'Otomatis', ukuran: '44.1 MB', status: 'berhasil' },
  { id: 5, waktu: '2025-06-22 02:00:00', tipe: 'Otomatis', ukuran: '43.9 MB', status: 'gagal' },
  { id: 6, waktu: '2025-06-21 02:00:00', tipe: 'Otomatis', ukuran: '43.5 MB', status: 'berhasil' },
];

const JADWAL_OPTIONS = [
  { label: 'Setiap hari', value: 'daily' },
  { label: 'Setiap minggu', value: 'weekly' },
  { label: 'Setiap bulan', value: 'monthly' },
];

const SettingsBackup = () => {
  const navigate = useNavigate();
  const [history] = useState(DEMO_HISTORY);
  const [autoBackup, setAutoBackup] = useState(true);
  const [jadwal, setJadwal] = useState('daily');

  const breadcrumbHome = { icon: 'pi pi-home', command: () => navigate('/dashboard') };
  const breadcrumbItems = [{ label: 'Sistem' }, { label: 'Backup Data' }];

  const totalBackup = history.length;
  const berhasil = history.filter((h) => h.status === 'berhasil').length;
  const gagal = history.filter((h) => h.status === 'gagal').length;
  const lastBackup = history[0]?.waktu || '-';

  /* ---------- Column Templates ---------- */
  const waktuTemplate = (row: (typeof DEMO_HISTORY)[0]) => (
    <div>
      <div style={{ fontWeight: 500, fontSize: '0.85rem' }}>{new Date(row.waktu).toLocaleDateString('id-ID', { day: 'numeric', month: 'short', year: 'numeric' })}</div>
      <div style={{ fontSize: '0.75rem', color: '#6b7280' }}>{row.waktu.split(' ')[1]}</div>
    </div>
  );

  const tipeTemplate = (row: (typeof DEMO_HISTORY)[0]) => (
    <Tag value={row.tipe} severity={row.tipe === 'Manual' ? 'info' : 'warning'} rounded icon={row.tipe === 'Manual' ? 'pi pi-user' : 'pi pi-clock'} />
  );

  const statusTemplate = (row: (typeof DEMO_HISTORY)[0]) => (
    <Tag value={row.status === 'berhasil' ? 'Berhasil' : 'Gagal'} severity={row.status === 'berhasil' ? 'success' : 'danger'} rounded />
  );

  const actionTemplate = (row: (typeof DEMO_HISTORY)[0]) => (
    <div style={{ display: 'flex', gap: 8 }}>
      <Button icon="pi pi-download" rounded text severity="info" tooltip="Download" tooltipOptions={{ position: 'top' }} disabled={row.status === 'gagal'} />
      <Button icon="pi pi-replay" rounded text severity="warning" tooltip="Restore" tooltipOptions={{ position: 'top' }} disabled={row.status === 'gagal'} onClick={() => confirmDialog({ message: `Restore backup ${row.waktu}? Data saat ini akan diganti.`, header: 'Konfirmasi Restore', icon: 'pi pi-exclamation-triangle', acceptClassName: 'p-button-warning', accept: () => {} })} />
      <Button icon="pi pi-trash" rounded text severity="danger" tooltip="Hapus" tooltipOptions={{ position: 'top' }} onClick={() => confirmDialog({ message: 'Hapus backup ini?', header: 'Konfirmasi', icon: 'pi pi-exclamation-triangle', acceptClassName: 'p-button-danger', accept: () => {} })} />
    </div>
  );

  /* ---------- KPI Cards ---------- */
  const kpiCards = [
    { label: 'Total Backup', value: totalBackup, icon: 'pi pi-database', color: '#3B82F6' },
    { label: 'Berhasil', value: berhasil, icon: 'pi pi-check-circle', color: '#10B981' },
    { label: 'Gagal', value: gagal, icon: 'pi pi-times-circle', color: '#EF4444' },
    { label: 'Backup Terakhir', value: new Date(lastBackup).toLocaleDateString('id-ID', { day: 'numeric', month: 'short' }), icon: 'pi pi-clock', color: '#6366F1' },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <ConfirmDialog />

      <BreadCrumb model={breadcrumbItems} home={breadcrumbHome} />

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
        <h2 style={{ margin: 0, fontSize: '1.5rem', fontWeight: 700 }}>Backup Data</h2>
      </div>

      {/* KPI Cards */}
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16 }}>
        {kpiCards.map((kpi) => (
          <Card key={kpi.label} style={{ flex: '1 1 180px', borderLeft: `4px solid ${kpi.color}` }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
              <i className={kpi.icon} style={{ fontSize: '1.8rem', color: kpi.color }} />
              <div>
                <div style={{ fontSize: '1.6rem', fontWeight: 700 }}>{kpi.value}</div>
                <div style={{ color: '#6b7280', fontSize: '0.85rem' }}>{kpi.label}</div>
              </div>
            </div>
          </Card>
        ))}
      </div>

      {/* Actions Row */}
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16 }}>
        {/* Manual Backup */}
        <Card style={{ flex: '1 1 300px', borderLeft: '4px solid #3B82F6' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 12 }}>
            <i className="pi pi-download" style={{ fontSize: '1.3rem', color: '#3B82F6' }} />
            <span style={{ fontSize: '1.1rem', fontWeight: 700 }}>Backup Manual</span>
          </div>
          <p style={{ color: '#6b7280', fontSize: '0.9rem', marginBottom: 16 }}>Buat backup database secara manual. File backup akan diunduh dalam format SQL.</p>
          <div style={{ display: 'flex', gap: 8 }}>
            <Button label="Backup Database" icon="pi pi-database" onClick={() => confirmDialog({ message: 'Mulai backup database sekarang?', header: 'Konfirmasi', icon: 'pi pi-database', accept: () => {} })} />
            <Button label="Backup File" icon="pi pi-folder" severity="secondary" outlined />
          </div>
        </Card>

        {/* Auto Backup */}
        <Card style={{ flex: '1 1 300px', borderLeft: '4px solid #10B981' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 12 }}>
            <i className="pi pi-sync" style={{ fontSize: '1.3rem', color: '#10B981' }} />
            <span style={{ fontSize: '1.1rem', fontWeight: 700 }}>Backup Otomatis</span>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <span style={{ fontWeight: 500 }}>Status</span>
              <InputSwitch checked={autoBackup} onChange={(e) => setAutoBackup(e.value ?? false)} />
            </div>
            <Divider style={{ margin: '4px 0' }} />
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <span style={{ fontWeight: 500 }}>Jadwal</span>
              <Dropdown value={jadwal} options={JADWAL_OPTIONS} onChange={(e) => setJadwal(e.value)} style={{ width: 160 }} disabled={!autoBackup} />
            </div>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <span style={{ fontWeight: 500 }}>Retensi</span>
              <Tag value="30 hari" severity="info" />
            </div>
          </div>
        </Card>
      </div>

      {/* Backup History */}
      <Card>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 16 }}>
          <i className="pi pi-history" style={{ fontSize: '1.2rem', color: '#6366F1' }} />
          <span style={{ fontSize: '1.1rem', fontWeight: 700 }}>Riwayat Backup</span>
        </div>

        <DataTable value={history} paginator rows={10} emptyMessage="Belum ada riwayat backup" stripedRows removableSort dataKey="id" rowHover>
          <Column header="Waktu" body={waktuTemplate} sortable sortField="waktu" style={{ minWidth: 140 }} />
          <Column header="Tipe" body={tipeTemplate} sortable sortField="tipe" style={{ minWidth: 130 }} />
          <Column field="ukuran" header="Ukuran" sortable style={{ minWidth: 100 }} />
          <Column header="Status" body={statusTemplate} sortable sortField="status" style={{ minWidth: 100 }} />
          <Column header="Aksi" body={actionTemplate} style={{ minWidth: 150 }} />
        </DataTable>
      </Card>
    </div>
  );
};

export default SettingsBackup;
