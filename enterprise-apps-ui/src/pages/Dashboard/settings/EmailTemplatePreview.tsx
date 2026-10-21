import React, { useState } from 'react';
import {
  WorkflowNewReport,
  WorkflowPartialApproved,
  WorkflowFullyApproved,
  WorkflowDelegated,
  WorkflowCompleted,
  WorkflowReminder,
} from '../../../components/email-templates';

type TemplateKey =
  | 'new-report'
  | 'partial-approved'
  | 'fully-approved'
  | 'delegated'
  | 'completed'
  | 'reminder';

interface TemplateTab {
  key: TemplateKey;
  label: string;
  trigger: string;
  subject: string;
  color: string;
}

const tabs: TemplateTab[] = [
  {
    key: 'new-report',
    label: '1. New Report',
    trigger: 'Creator membuat laporan → Superior',
    subject: '[Action Required] Approval Laporan Baru: RPT-2026-0042 - Laporan Inspeksi Lab Kimia',
    color: '#2d6da8',
  },
  {
    key: 'partial-approved',
    label: '2. Partial Approved',
    trigger: 'Superior menyetujui → Manager',
    subject: '[Final Approval] Persetujuan Manajerial: RPT-2026-0042',
    color: '#7c3aed',
  },
  {
    key: 'fully-approved',
    label: '3. Fully Approved',
    trigger: 'Manager menyetujui → Manager Dept. Tujuan',
    subject: '[Action Required] Delegasi Laporan Masuk: RPT-2026-0042',
    color: '#0d9488',
  },
  {
    key: 'delegated',
    label: '4. Delegated',
    trigger: 'Manager Dept. mendelegasikan → Subordinate',
    subject: '[Assignment] Tugas Baru: RPT-2026-0042 - Laporan Inspeksi Lab Kimia',
    color: '#ea580c',
  },
  {
    key: 'completed',
    label: '5. Completed',
    trigger: 'Subordinate selesai → Creator & Manager',
    subject: '[Completed] Laporan Selesai & Ditutup: RPT-2026-0042',
    color: '#16a34a',
  },
  {
    key: 'reminder',
    label: '6. Reminder',
    trigger: 'Tindakan belum dilakukan setelah N hari → Pemegang aksi',
    subject: '[Reminder] Tindakan Belum Dilakukan: RPT-2026-0042 - Laporan Inspeksi Lab Kimia',
    color: '#d97706',
  },
];

const sampleData = {
  recipientName: 'Budi Santoso',
  creatorName: 'Andi Wijaya',
  approverName: 'Budi Santoso',
  delegatorName: 'Pak Darmawan',
  completedByName: 'Sari Dewi',
  reportId: 'RPT-2026-0042',
  reportTitle: 'Laporan Inspeksi Keselamatan Lab Kimia Q1',
  submissionDate: '7 April 2026',
  completionDate: '14 April 2026',
  deadline: '14 April 2026',
  actionUrl: '#preview',
};

const EmailTemplatePreview: React.FC = () => {
  const [active, setActive] = useState<TemplateKey>('new-report');
  const currentTab = tabs.find((t) => t.key === active)!;

  const renderTemplate = () => {
    switch (active) {
      case 'new-report':
        return (
          <WorkflowNewReport
            recipientName={sampleData.recipientName}
            creatorName={sampleData.creatorName}
            reportId={sampleData.reportId}
            reportTitle={sampleData.reportTitle}
            submissionDate={sampleData.submissionDate}
            actionUrl={sampleData.actionUrl}
          />
        );
      case 'partial-approved':
        return (
          <WorkflowPartialApproved
            recipientName={sampleData.recipientName}
            creatorName={sampleData.creatorName}
            approverName={sampleData.approverName}
            reportId={sampleData.reportId}
            reportTitle={sampleData.reportTitle}
            actionUrl={sampleData.actionUrl}
          />
        );
      case 'fully-approved':
        return (
          <WorkflowFullyApproved
            recipientName="Pak Darmawan"
            creatorName={sampleData.creatorName}
            approverName={sampleData.recipientName}
            reportId={sampleData.reportId}
            reportTitle={sampleData.reportTitle}
            actionUrl={sampleData.actionUrl}
          />
        );
      case 'delegated':
        return (
          <WorkflowDelegated
            recipientName="Sari Dewi"
            delegatorName={sampleData.delegatorName}
            reportId={sampleData.reportId}
            reportTitle={sampleData.reportTitle}
            deadline={sampleData.deadline}
            actionUrl={sampleData.actionUrl}
          />
        );
      case 'completed':
        return (
          <WorkflowCompleted
            recipientName={sampleData.creatorName}
            completedByName={sampleData.completedByName}
            reportId={sampleData.reportId}
            reportTitle={sampleData.reportTitle}
            completionDate={sampleData.completionDate}
            actionUrl={sampleData.actionUrl}
          />
        );
      case 'reminder':
        return (
          <WorkflowReminder
            recipientName={sampleData.recipientName}
            reportId={sampleData.reportId}
            reportTitle={sampleData.reportTitle}
            pendingAction="Review Laporan"
            currentStatus="PENDING_REVIEW"
            daysPending={3}
            submissionDate={sampleData.submissionDate}
            classificationName="Near Miss"
            categoryName="Keselamatan Kerja"
            actionUrl={sampleData.actionUrl}
          />
        );
    }
  };

  return (
    <div className="p-6">
      {/* Page Header */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-800">Email Template Preview</h1>
        <p className="text-gray-500 mt-1">
          Preview 6 tahapan email notifikasi workflow persetujuan laporan
        </p>
      </div>

      {/* Tab Navigation */}
      <div className="flex flex-wrap gap-2 mb-6">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActive(tab.key)}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition-all duration-200 ${
              active === tab.key
                ? 'text-white shadow-lg scale-105'
                : 'bg-white text-gray-600 border border-gray-200 hover:border-gray-300 hover:shadow'
            }`}
            style={active === tab.key ? { backgroundColor: tab.color } : undefined}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Info Bar */}
      <div className="bg-white rounded-lg border border-gray-200 p-4 mb-6">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <span className="text-xs font-semibold text-gray-400 uppercase tracking-wider">Trigger</span>
            <p className="text-sm text-gray-700 mt-1">{currentTab.trigger}</p>
          </div>
          <div>
            <span className="text-xs font-semibold text-gray-400 uppercase tracking-wider">Subject Line</span>
            <p className="text-sm text-gray-700 mt-1 font-mono bg-gray-50 px-2 py-1 rounded">
              {currentTab.subject}
            </p>
          </div>
        </div>
      </div>

      {/* Email Preview */}
      <div className="bg-gray-100 rounded-xl p-4 border border-gray-200">
        <div className="flex items-center gap-2 mb-3">
          <div className="w-3 h-3 rounded-full bg-red-400" />
          <div className="w-3 h-3 rounded-full bg-yellow-400" />
          <div className="w-3 h-3 rounded-full bg-green-400" />
          <span className="text-xs text-gray-400 ml-2">Email Preview — {currentTab.label}</span>
        </div>
        <div className="bg-[#f4f6f9] rounded-lg overflow-auto max-h-[75vh]">
          {renderTemplate()}
        </div>
      </div>
    </div>
  );
};

export default EmailTemplatePreview;
