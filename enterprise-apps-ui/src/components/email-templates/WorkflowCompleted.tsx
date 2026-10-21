import React from 'react';
import {
  EmailShell,
  EmailHeader,
  EmailFooter,
  InfoCard,
  InfoRow,
  CtaButton,
  ApprovalTrail,
  EmailTemplateProps,
  styles,
} from './EmailComponents';

export interface CompletedEmailProps extends EmailTemplateProps {
  completedByName: string;
  completionDate: string;
}

const WorkflowCompleted: React.FC<CompletedEmailProps> = ({
  recipientName,
  completedByName,
  reportId,
  reportTitle,
  completionDate,
  actionUrl,
  classificationName,
  categoryName,
  reviewedBy,
  acknowledgedBy,
}) => (
  <EmailShell>
    <EmailHeader
      gradient="linear-gradient(135deg, #14532d, #16a34a)"
      icon="🎉"
      title="Laporan Telah Selesai!"
      badgeText="COMPLETED"
      badgeBg="#22c55e"
    />
    <tr>
      <td style={styles.bodyTd}>
        <p style={styles.greeting}>
          Halo <strong>{recipientName}</strong>,
        </p>
        <p style={styles.paragraph}>
          Kami dengan senang hati menginformasikan bahwa laporan Anda telah{' '}
          <strong>selesai diproses</strong> oleh seluruh pihak terkait. Seluruh inputan
          telah dilengkapi.
        </p>

        <InfoCard borderColor="#16a34a" bgColor="#f0fdf4">
          <InfoRow label="ID Laporan" value={reportId} />
          <InfoRow label="Judul Laporan" value={reportTitle} />
          <InfoRow label="Diselesaikan Oleh" value={completedByName} />
          <InfoRow label="Tanggal Selesai" value={completionDate} />
          {classificationName && <InfoRow label="Klasifikasi Insiden" value={classificationName} />}
          {categoryName && <InfoRow label="Kategori Insiden" value={categoryName} />}
          {reviewedBy && <InfoRow label="Reviewed By" value={reviewedBy} />}
          {acknowledgedBy && <InfoRow label="Acknowledged By" value={acknowledgedBy} />}
          <InfoRow label="Status Akhir" badge={{ text: 'DONE', color: '#14532d', bg: '#bbf7d0' }} />
          <InfoRow label="Status Laporan" badge={{ text: 'CLOSED', color: '#374151', bg: '#e5e7eb' }} />
        </InfoCard>

        <ApprovalTrail
          steps={[
            { label: 'Dibuat oleh Creator', done: true },
            { label: 'Disetujui Superior (Partially Approved)', done: true },
            { label: 'Disetujui Manager (Fully Approved)', done: true },
            { label: 'Didelegasikan ke Dept. Tujuan', done: true },
            { label: 'Selesai & Ditutup', done: true, current: true },
          ]}
        />

        <p style={styles.paragraph}>
          Anda dapat melihat hasil akhir laporan lengkap melalui sistem.
        </p>

        <CtaButton
          gradient="linear-gradient(135deg, #16a34a, #14532d)"
          href={actionUrl}
          icon="📄"
          label="Lihat Laporan Final"
          linkColor="#16a34a"
        />
      </td>
    </tr>
    <EmailFooter />
  </EmailShell>
);

export default WorkflowCompleted;
