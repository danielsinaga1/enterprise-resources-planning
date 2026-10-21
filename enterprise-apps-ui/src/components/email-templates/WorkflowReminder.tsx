import React from 'react';
import {
  EmailShell,
  EmailHeader,
  EmailFooter,
  InfoCard,
  InfoRow,
  CtaButton,
  CalloutBox,
  PendingBanner,
  EmailTemplateProps,
  styles,
} from './EmailComponents';

export interface ReminderEmailProps extends EmailTemplateProps {
  pendingAction: string;
  currentStatus: string;
  daysPending: number;
  submissionDate: string;
}

const WorkflowReminder: React.FC<ReminderEmailProps> = ({
  recipientName,
  reportId,
  reportTitle,
  actionUrl,
  pendingAction,
  currentStatus,
  daysPending,
  submissionDate,
  classificationName,
  categoryName,
}) => (
  <EmailShell>
    <EmailHeader
      gradient="linear-gradient(135deg, #92400e, #d97706)"
      icon="⏳"
      title="Pengingat: Tindakan Segera Diperlukan"
      badgeText="REMINDER"
      badgeBg="#fef3c7"
    />

    <PendingBanner daysPending={daysPending} />

    <tr>
      <td style={styles.bodyTd}>
        <p style={styles.greeting}>
          Halo <strong>{recipientName}</strong>,
        </p>
        <p style={styles.paragraph}>
          Ini adalah pengingat bahwa laporan berikut masih menunggu{' '}
          <strong>{pendingAction}</strong> dari Anda. Mohon segera ditindaklanjuti
          agar proses tidak terhambat.
        </p>

        <InfoCard borderColor="#d97706" bgColor="#fffbeb">
          <InfoRow label="Nomor Laporan" value={reportId} />
          <InfoRow label="Judul Laporan" value={reportTitle} />
          <InfoRow label="Tanggal Dibuat" value={submissionDate} />
          {classificationName && (
            <InfoRow label="Klasifikasi Insiden" value={classificationName} />
          )}
          {categoryName && (
            <InfoRow label="Kategori Insiden" value={categoryName} />
          )}
          <InfoRow
            label="Tindakan Diperlukan"
            badge={{ text: pendingAction, color: '#92400e', bg: '#fef3c7' }}
          />
          <InfoRow
            label="Status Saat Ini"
            badge={{ text: currentStatus, color: '#b45309', bg: '#fef9c3' }}
          />
          <InfoRow
            label="Menunggu Sejak"
            value={`${daysPending} hari yang lalu`}
          />
        </InfoCard>

        <CalloutBox
          icon="🚨"
          title="Mengapa Ini Penting?"
          titleColor="#b91c1c"
          bgColor="#fef2f2"
          borderColor="#fecaca"
        >
          Keterlambatan tindakan dapat menghambat seluruh alur persetujuan laporan
          dan berpotensi memengaruhi penanganan insiden secara keseluruhan.
          Jika Anda memerlukan informasi tambahan, hubungi pelapor atau administrator sistem.
        </CalloutBox>

        <CtaButton
          gradient="linear-gradient(135deg, #d97706, #92400e)"
          href={actionUrl}
          icon="▶"
          label="Tindak Lanjuti Sekarang"
          linkColor="#d97706"
        />
      </td>
    </tr>

    <EmailFooter />
  </EmailShell>
);

export default WorkflowReminder;
