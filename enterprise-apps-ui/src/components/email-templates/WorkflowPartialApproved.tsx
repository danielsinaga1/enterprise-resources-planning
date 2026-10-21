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

export interface PartialApprovedEmailProps extends EmailTemplateProps {
  creatorName: string;
  approverName: string;
}

const WorkflowPartialApproved: React.FC<PartialApprovedEmailProps> = ({
  recipientName,
  creatorName,
  approverName,
  reportId,
  reportTitle,
  actionUrl,
  classificationName,
  categoryName,
  reviewedBy,
}) => (
  <EmailShell>
    <EmailHeader
      gradient="linear-gradient(135deg, #4c1d95, #7c3aed)"
      icon="✅"
      title="Persetujuan Akhir Diperlukan"
      badgeText="FINAL APPROVAL"
      badgeBg="#f59e0b"
    />
    <tr>
      <td style={styles.bodyTd}>
        <p style={styles.greeting}>
          Halo <strong>{recipientName}</strong>,
        </p>
        <p style={styles.paragraph}>
          Laporan berikut telah disetujui oleh Superior dan kini memerlukan{' '}
          <strong>persetujuan akhir</strong> Anda untuk status <em>Fully Approved</em>.
        </p>

        <InfoCard borderColor="#7c3aed" bgColor="#faf5ff">
          <InfoRow label="ID Laporan" value={reportId} />
          <InfoRow label="Judul Laporan" value={reportTitle} />
          <InfoRow label="Pengaju" value={creatorName} />
          <InfoRow label="Disetujui Oleh" value={approverName} />
          {classificationName && <InfoRow label="Klasifikasi Insiden" value={classificationName} />}
          {categoryName && <InfoRow label="Kategori Insiden" value={categoryName} />}
          {reviewedBy && <InfoRow label="Reviewed By" value={reviewedBy} />}
          <InfoRow label="Status Saat Ini" badge={{ text: 'PARTIALLY APPROVED', color: '#5b21b6', bg: '#ede9fe' }} />
        </InfoCard>

        <ApprovalTrail
          steps={[
            { label: 'Creator → Submitted', done: true },
            { label: 'Superior → Approved', done: true },
            { label: 'Manager → Menunggu Persetujuan Anda', done: false, current: true },
          ]}
        />

        <p style={styles.paragraph}>
          Silakan berikan keputusan Anda melalui tautan di bawah ini.
        </p>

        <CtaButton
          gradient="linear-gradient(135deg, #7c3aed, #4c1d95)"
          href={actionUrl}
          icon="✅"
          label="Berikan Persetujuan"
          linkColor="#7c3aed"
        />
      </td>
    </tr>
    <EmailFooter />
  </EmailShell>
);

export default WorkflowPartialApproved;
