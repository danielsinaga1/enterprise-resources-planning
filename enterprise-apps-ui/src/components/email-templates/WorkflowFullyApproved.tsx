import React from 'react';
import {
  EmailShell,
  EmailHeader,
  EmailFooter,
  InfoCard,
  InfoRow,
  CtaButton,
  CalloutBox,
  EmailTemplateProps,
  styles,
} from './EmailComponents';

export interface FullyApprovedEmailProps extends EmailTemplateProps {
  creatorName: string;
  approverName: string;
}

const WorkflowFullyApproved: React.FC<FullyApprovedEmailProps> = ({
  recipientName,
  creatorName,
  approverName,
  reportId,
  reportTitle,
  actionUrl,
  classificationName,
  categoryName,
  reviewedBy,
  acknowledgedBy,
}) => (
  <EmailShell>
    <EmailHeader
      gradient="linear-gradient(135deg, #065f46, #0d9488)"
      icon="📥"
      title="Laporan Masuk untuk Departemen Anda"
      badgeText="FULLY APPROVED"
      badgeBg="#22c55e"
    />
    <tr>
      <td style={styles.bodyTd}>
        <p style={styles.greeting}>
          Halo <strong>{recipientName}</strong>,
        </p>
        <p style={styles.paragraph}>
          Laporan berikut telah mendapatkan <strong>persetujuan penuh</strong> (Fully Approved)
          dan sekarang dialokasikan ke departemen Anda untuk ditindaklanjuti.
        </p>

        <InfoCard borderColor="#0d9488" bgColor="#f0fdf4">
          <InfoRow label="ID Laporan" value={reportId} />
          <InfoRow label="Judul Laporan" value={reportTitle} />
          <InfoRow label="Pengaju Asal" value={creatorName} />
          <InfoRow label="Disetujui Oleh" value={approverName} />
          {classificationName && <InfoRow label="Klasifikasi Insiden" value={classificationName} />}
          {categoryName && <InfoRow label="Kategori Insiden" value={categoryName} />}
          {reviewedBy && <InfoRow label="Reviewed By" value={reviewedBy} />}
          {acknowledgedBy && <InfoRow label="Acknowledged By" value={acknowledgedBy} />}
          <InfoRow label="Status" badge={{ text: 'FULLY APPROVED', color: '#065f46', bg: '#d1fae5' }} />
        </InfoCard>

        <CalloutBox
          icon="🔍"
          title="Langkah Selanjutnya"
          titleColor="#0d9488"
          bgColor="transparent"
          borderColor="#d1d5db"
          borderStyle="dashed"
        >
          Silakan delegasikan laporan ini kepada subordinat/staf Anda untuk segera
          ditindaklanjuti dan diinput datanya ke dalam sistem.
        </CalloutBox>

        <CtaButton
          gradient="linear-gradient(135deg, #0d9488, #065f46)"
          href={actionUrl}
          icon="👉"
          label="Delegasikan Tugas"
          linkColor="#0d9488"
        />
      </td>
    </tr>
    <EmailFooter />
  </EmailShell>
);

export default WorkflowFullyApproved;
