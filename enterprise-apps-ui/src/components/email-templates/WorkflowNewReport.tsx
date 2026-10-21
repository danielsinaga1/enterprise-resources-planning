import React from 'react';
import {
  EmailShell,
  EmailHeader,
  EmailFooter,
  InfoCard,
  InfoRow,
  CtaButton,
  EmailTemplateProps,
  styles,
} from './EmailComponents';

export interface NewReportEmailProps extends EmailTemplateProps {
  creatorName: string;
  
  submissionDate: string;
}

const WorkflowNewReport: React.FC<NewReportEmailProps> = ({
  recipientName,
  creatorName,
  reportId,
  reportTitle,
  submissionDate,
  actionUrl,
  classificationName,
  categoryName,
}) => (
  <EmailShell>
    <EmailHeader
      gradient="linear-gradient(135deg, #1e3a5f, #2d6da8)"
      icon="📋"
      title="Laporan Baru Memerlukan Tinjauan"
      badgeText="ACTION REQUIRED"
      badgeBg="#f59e0b"
    />
    <tr>
      <td style={styles.bodyTd}>
        <p style={styles.greeting}>
          Halo <strong>{recipientName}</strong>,
        </p>
        <p style={styles.paragraph}>
          Laporan baru telah dibuat oleh <strong>{creatorName}</strong> dan memerlukan
          tinjauan Anda untuk tahap persetujuan pertama.
        </p>

        <InfoCard borderColor="#2d6da8" bgColor="#f0f7ff">
          <InfoRow label="ID Laporan" value={reportId} />
          <InfoRow label="Judul Laporan" value={reportTitle} />
          <InfoRow label="Tanggal Pengajuan" value={submissionDate} />
          <InfoRow label="Pengaju" value={creatorName} />
          {classificationName && <InfoRow label="Klasifikasi Insiden" value={classificationName} />}
          {categoryName && <InfoRow label="Kategori Insiden" value={categoryName} />}
          <InfoRow label="Status" badge={{ text: 'NEW', color: '#1e3a5f', bg: '#dbeafe' }} />
        </InfoCard>

        <p style={styles.paragraph}>
          Mohon tinjau laporan ini dan berikan keputusan Anda untuk mengubah status
          menjadi <strong>Partially Approved</strong>.
        </p>

        <CtaButton
          gradient="linear-gradient(135deg, #2d6da8, #1e3a5f)"
          href={actionUrl}
          icon="▶"
          label="Review Laporan"
          linkColor="#2d6da8"
        />
      </td>
    </tr>
    <EmailFooter />
  </EmailShell>
);

export default WorkflowNewReport;
