/**
 * Dokumen cetak Jasa Maklon: Surat Jalan Pengembalian Titipan (kertas karbon, hanya kuantitas) dan
 * Berita Acara Hasil Proses (rekap order: titipan, produksi, rendemen, pengembalian, sisa).
 */
import React from 'react';
import { Document, Page, Text, View } from '@react-pdf/renderer';
import { PdfFooter, PdfHeader, PdfMetaField, PdfMetaRow, PdfPartyBlock, PdfSection, PdfSignatureBlock, PdfTable, type PdfCompany } from './kit';
import { NcrItemsTable, NcrNotes, type NcrItem } from './ncrParts';
import { FOOTER_SPACE, NCR_PAGE } from './theme';
import { pdfDate, pdfNum, pdfT } from './pdfFormat';

export interface CustomerReturnPDFProps {
  returnNumber: string;
  date: string;
  party: { name: string; address?: string | null };
  tollOrderNumber?: string | null;
  nomorMobil?: string | null;
  driverName?: string | null;
  recipientName?: string | null;
  notes?: string | null;
  items: NcrItem[];
  company: PdfCompany;
}

export const CustomerReturnPDF: React.FC<CustomerReturnPDFProps> = ({
  returnNumber, date, party, tollOrderNumber, nomorMobil, driverName, recipientName, notes, items, company,
}) => {
  const t = pdfT();
  return (
    <Document title={t('toll.return.docTitle', { number: returnNumber })} author={company.name}>
      <Page size="A4" style={{ ...NCR_PAGE, paddingBottom: FOOTER_SPACE }} wrap>
        <PdfHeader company={company} title={t('toll.return.title')} number={returnNumber} tone="mono" pageMeta />
        <PdfMetaRow gap={16}>
          <View style={{ flex: 1 }}>
            <PdfMetaField tone="mono" label={t('toll.return.date')} value={pdfDate(date)} bold />
            <PdfPartyBlock tone="mono" label={t('toll.return.to')} name={party.name}
              lines={[party.address, tollOrderNumber && t('toll.orderRef', { number: tollOrderNumber })]} />
          </View>
          <View style={{ flex: 1 }}>
            <PdfMetaField tone="mono" label={t('common.vehicleNo')} value={nomorMobil ? nomorMobil.toUpperCase() : null} bold />
            <PdfMetaField tone="mono" label={t('toll.return.driver')} value={driverName} bold />
            <PdfMetaField tone="mono" label={t('toll.return.recipient')} value={recipientName} bold />
          </View>
        </PdfMetaRow>
        <NcrItemsTable items={items} />
        <NcrNotes notes={notes ?? t('toll.return.ownershipNote')} />
        <PdfSignatureBlock
          tone="mono"
          signatures={[
            { label: t('toll.return.signatures.warehouse') },
            { label: t('toll.return.signatures.driver') },
            { label: t('toll.return.signatures.recipient') },
          ]}
        />
        <PdfFooter tone="mono" company={company.name} docRef={returnNumber} />
      </Page>
    </Document>
  );
};

type N = string | number | null | undefined;

export interface TollProcessReportPDFProps {
  orderNumber: string;
  date: string;
  party: { name: string };
  serviceName?: string | null;
  status: string;
  stock: Array<{ itemName: string; unit: string; receivedQty: N; processedQty: N; producedQty: N; returnedQty: N; balanceQty: N }>;
  production: { runs: number; inputQty: N; mainOutputQty: N; byProductQty: N; shrinkQty: N; yieldPct: N; expectedYieldPct: N; shrinkPct: N };
  runs: Array<{ runNumber: string; date: string; inputQty: N; outputQty: N; yieldPct: N; isCancelled: boolean }>;
  factoryByProducts: Array<{ itemName: string; unit: string; quantity: N }>;
  company: PdfCompany;
}

const pct = (v: N) => (v === null || v === undefined || v === '' ? '—' : `${pdfNum(v, 2)}%`);

export const TollProcessReportPDF: React.FC<TollProcessReportPDFProps> = ({
  orderNumber, date, party, serviceName, status, stock, production, runs, factoryByProducts, company,
}) => {
  const t = pdfT();
  return (
    <Document title={t('toll.report.docTitle', { number: orderNumber })} author={company.name}>
      <Page size="A4" style={{ ...NCR_PAGE, paddingBottom: FOOTER_SPACE }} wrap>
        <PdfHeader company={company} title={t('toll.report.title')} number={orderNumber} tone="mono" pageMeta />
        <PdfMetaRow gap={16}>
          <View style={{ flex: 1 }}>
            <PdfPartyBlock tone="mono" label={t('toll.report.customer')} name={party.name} lines={[serviceName]} />
          </View>
          <View style={{ flex: 1 }}>
            <PdfMetaField tone="mono" label={t('toll.report.orderDate')} value={pdfDate(date)} bold />
            <PdfMetaField tone="mono" label={t('toll.report.status')} value={status} bold />
          </View>
        </PdfMetaRow>

        <PdfSection title={t('toll.report.summary')} tone="mono">
          <PdfTable
            tone="mono"
            zebra={false}
            repeatHeader={false}
            columns={[
              { key: 'input', label: t('toll.report.input'), width: 1, align: 'right' },
              { key: 'output', label: t('toll.report.mainOutput'), width: 1, align: 'right' },
              { key: 'by', label: t('toll.report.byProduct'), width: 1, align: 'right' },
              { key: 'shrink', label: t('toll.report.shrink'), width: 1, align: 'right' },
              { key: 'yield', label: t('toll.report.yield'), width: 1, align: 'right' },
              { key: 'target', label: t('toll.report.target'), width: 1, align: 'right' },
            ]}
            rows={[{
              input: pdfNum(production.inputQty, 3),
              output: pdfNum(production.mainOutputQty, 3),
              by: pdfNum(production.byProductQty, 3),
              shrink: `${pdfNum(production.shrinkQty, 3)} (${pct(production.shrinkPct)})`,
              yield: pct(production.yieldPct),
              target: pct(production.expectedYieldPct),
            }]}
          />
        </PdfSection>

        <PdfSection title={t('toll.report.stock')} tone="mono">
          <PdfTable
            tone="mono"
            zebra={false}
            repeatHeader={false}
            emptyText="—"
            columns={[
              { key: 'item', label: t('toll.report.item'), width: 2.4 },
              { key: 'received', label: t('toll.report.received'), width: 1, align: 'right' },
              { key: 'processed', label: t('toll.report.processed'), width: 1, align: 'right' },
              { key: 'produced', label: t('toll.report.produced'), width: 1, align: 'right' },
              { key: 'returned', label: t('toll.report.returned'), width: 1, align: 'right' },
              { key: 'balance', label: t('toll.report.balance'), width: 1, align: 'right' },
            ]}
            rows={stock.map((s) => ({
              item: `${s.itemName} (${s.unit})`,
              received: pdfNum(s.receivedQty, 3),
              processed: pdfNum(s.processedQty, 3),
              produced: pdfNum(s.producedQty, 3),
              returned: pdfNum(s.returnedQty, 3),
              balance: pdfNum(s.balanceQty, 3),
            }))}
          />
        </PdfSection>

        <PdfSection title={t('toll.report.runs', { count: production.runs })} tone="mono">
          <PdfTable
            tone="mono"
            zebra={false}
            repeatHeader={false}
            emptyText="—"
            columns={[
              { key: 'no', label: t('toll.report.runNumber'), width: 1.6 },
              { key: 'date', label: t('toll.report.runDate'), width: 1.4 },
              { key: 'input', label: t('toll.report.input'), width: 1, align: 'right' },
              { key: 'output', label: t('toll.report.mainOutput'), width: 1, align: 'right' },
              { key: 'yield', label: t('toll.report.yield'), width: 1, align: 'right' },
            ]}
            rows={runs.filter((r) => !r.isCancelled).map((r) => ({
              no: r.runNumber,
              date: pdfDate(r.date),
              input: pdfNum(r.inputQty, 3),
              output: pdfNum(r.outputQty, 3),
              yield: pct(r.yieldPct),
            }))}
          />
        </PdfSection>

        {factoryByProducts.length > 0 && (
          <View style={{ marginTop: 8 }}>
            <Text style={{ fontSize: 9 }}>
              {t('toll.report.factoryByProducts', {
                items: factoryByProducts.map((b) => `${b.itemName} ${pdfNum(b.quantity, 3)} ${b.unit}`).join(', '),
              })}
            </Text>
          </View>
        )}

        <PdfSignatureBlock
          tone="mono"
          signatures={[
            { label: t('toll.report.signatures.production') },
            { label: t('toll.report.signatures.customer') },
          ]}
        />
        <PdfFooter tone="mono" company={company.name} docRef={orderNumber} />
      </Page>
    </Document>
  );
};
