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

export interface DelegatedEmailProps extends EmailTemplateProps {
  delegatorName: string;
  deadline?: string;
}

const WorkflowDelegated: React.FC<DelegatedEmailProps> = ({
  recipientName,
  delegatorName,
  reportId,
  reportTitle,
  deadline,
  actionUrl,
  classificationName,
  categoryName,
  reviewedBy,
  acknowledgedBy,
}) => (
  <EmailShell>
    <EmailHeader
      gradient="linear-gradient(135deg, #9a3412, #ea580c)"
      icon="🛠"
      title="Tugas Baru Diberikan kepada Anda"
      badgeText="IN PROGRESS"
      badgeBg="#3b82f6"
    />
    <tr>
      <td style={styles.bodyTd}>
        <p style={styles.greeting}>
          Halo <strong>{recipientName}</strong>,
        </p>
        <p style={styles.paragraph}>
          Anda telah didelegasikan untuk mengerjakan laporan berikut oleh{' '}
          <strong>{delegatorName}</strong>. Mohon segera ditindaklanjuti.
        </p>

        <InfoCard borderColor="#ea580c" bgColor="#fff7ed">
          <InfoRow label="ID Laporan" value={reportId} />
          <InfoRow label="Judul Laporan" value={reportTitle} />
          <InfoRow label="Didelegasikan Oleh" value={delegatorName} />
          {classificationName && <InfoRow label="Klasifikasi Insiden" value={classificationName} />}
          {categoryName && <InfoRow label="Kategori Insiden" value={categoryName} />}
          {reviewedBy && <InfoRow label="Reviewed By" value={reviewedBy} />}
          {acknowledgedBy && <InfoRow label="Acknowledged By" value={acknowledgedBy} />}
          <InfoRow label="Status" badge={{ text: 'IN PROGRESS', color: '#1e40af', bg: '#dbeafe' }} />
          <InfoRow label="State" badge={{ text: 'OPEN', color: '#9a3412', bg: '#ffedd5' }} />
        </InfoCard>

        <CalloutBox
          icon="⚠"
          title="Tugas Anda"
          titleColor="#92400e"
          bgColor="#fffbeb"
          borderColor="#fde68a"
        >
          Mengisi dan melengkapi seluruh input data yang tersedia di sistem sesuai
          dengan laporan yang telah disetujui. Mohon diselesaikan sesuai dengan
          tenggat waktu yang ditentukan.
        </CalloutBox>

        {deadline && (
          <p style={{ margin: '0 0 24px', fontSize: '14px', color: '#dc2626', fontWeight: 600 }}>
            ⏰ Tenggat Waktu: {deadline}
          </p>
        )}

        <CtaButton
          gradient="linear-gradient(135deg, #ea580c, #9a3412)"
          href={actionUrl}
          icon="🚀"
          label="Mulai Pengerjaan"
          linkColor="#ea580c"
        />
      </td>
    </tr>
    <EmailFooter />
  </EmailShell>
);

export default WorkflowDelegated;
