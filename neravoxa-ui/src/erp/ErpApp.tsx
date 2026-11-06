/**
 * Pohon rute modul ERP (port App.tsx ENTARIS) — dipasang platform di `/dashboard/erp/*`.
 *
 * Path di sini RELATIF terhadap `/dashboard/erp`; kode halaman tetap memakai path absolut
 * ENTARIS (`/sales`, `/ga/vehicle`) yang diterjemahkan `@/lib/router`. Login, sidebar dan topbar
 * milik platform; ERP hanya menambah header ringkas (breadcrumb, pencarian, notifikasi).
 */
import { Navigate, Route, Routes } from 'react-router-dom';
import type { ReactNode } from 'react';
import { lazyPage } from './app/lazy';
import { can } from './lib/permissions';
import { ErpShell } from './ErpShell';
import { ErpLayout } from './ErpLayout';

const Dashboard = lazyPage(() => import('./pages/Dashboard').then((m) => ({ default: m.Dashboard })));
const GeneralLedger = lazyPage(() => import('./pages/GeneralLedger').then((m) => ({ default: m.GeneralLedger })));
const COAPage = lazyPage(() => import('./pages/COAPage').then((m) => ({ default: m.COAPage })));
const SalesInvoices = lazyPage(() => import('./pages/SalesInvoices').then((m) => ({ default: m.SalesInvoices })));
const PurchaseInvoices = lazyPage(() => import('./pages/PurchaseInvoices').then((m) => ({ default: m.PurchaseInvoices })));
const Quotations = lazyPage(() => import('./pages/Quotations'));
const SalesOrders = lazyPage(() => import('./pages/SalesOrders'));
const SalesReturnsPage = lazyPage(() => import('./pages/SalesReturnsPage'));
const Inquiries = lazyPage(() => import('./pages/Inquiries'));
const Warehouses = lazyPage(() => import('./pages/Warehouses'));
const WarehouseStockDashboard = lazyPage(() => import('./pages/WarehouseStockDashboard'));
const StockTransfers = lazyPage(() => import('./pages/StockTransfers'));
const DeliveryOrders = lazyPage(() => import('./pages/DeliveryOrders'));
const GoodsReceipts = lazyPage(() => import('./pages/GoodsReceipts'));
const TollService = lazyPage(() => import('./pages/TollService'));
const PurchaseOrders = lazyPage(() => import('./pages/PurchaseOrders'));
const Payments = lazyPage(() => import('./pages/Payments').then((m) => ({ default: m.Payments })));
const PartiesPage = lazyPage(() => import('./pages/PartiesPage').then((m) => ({ default: m.PartiesPage })));
const InventoryPage = lazyPage(() => import('./pages/InventoryPage').then((m) => ({ default: m.InventoryPage })));
const StockOpnamePage = lazyPage(() => import('./pages/StockOpnamePage').then((m) => ({ default: m.StockOpnamePage })));
const Reports = lazyPage(() => import('./pages/Reports'));
const TrialBalance = lazyPage(() => import('./pages/reports/TrialBalance'));
const ProfitLoss = lazyPage(() => import('./pages/reports/ProfitLoss'));
const BalanceSheet = lazyPage(() => import('./pages/reports/BalanceSheet'));
const AgingAnalysis = lazyPage(() => import('./pages/reports/AgingAnalysis'));
const CashFlow = lazyPage(() => import('./pages/reports/CashFlow'));
const SettingsPage = lazyPage(() => import('./pages/Settings').then((m) => ({ default: m.SettingsPage })));
const UserManagement = lazyPage(() => import('./pages/UserManagement').then((m) => ({ default: m.UserManagement })));
const AuditTrail = lazyPage(() => import('./pages/AuditTrail').then((m) => ({ default: m.AuditTrail })));
const Notifications = lazyPage(() => import('./pages/Notifications').then((m) => ({ default: m.Notifications })));
const RecurringTransactions = lazyPage(() => import('./pages/RecurringTransactions').then((m) => ({ default: m.RecurringTransactions })));
const BankReconciliation = lazyPage(() => import('./pages/BankReconciliation').then((m) => ({ default: m.BankReconciliation })));
const VendorDeposits = lazyPage(() => import('./pages/VendorDeposits').then((m) => ({ default: m.VendorDeposits })));
const ProjectGuarantees = lazyPage(() => import('./pages/ProjectGuarantees').then((m) => ({ default: m.ProjectGuarantees })));
const GaDashboardPage = lazyPage(() => import('./pages/ga/GaDashboardPage').then((m) => ({ default: m.GaDashboardPage })));
const GaAssetsPage = lazyPage(() => import('./pages/ga/GaAssetsPage').then((m) => ({ default: m.GaAssetsPage })));
const GaDocumentsPage = lazyPage(() => import('./pages/ga/GaDocumentsPage').then((m) => ({ default: m.GaDocumentsPage })));
const GaInventoryPage = lazyPage(() => import('./pages/ga/GaInventoryPage').then((m) => ({ default: m.GaInventoryPage })));
const GaMaintenancePage = lazyPage(() => import('./pages/ga/GaMaintenancePage').then((m) => ({ default: m.GaMaintenancePage })));
const GaMaterialRequestPage = lazyPage(() => import('./pages/ga/GaMaterialRequestPage').then((m) => ({ default: m.GaMaterialRequestPage })));
const GaVehiclePage = lazyPage(() => import('./pages/ga/GaVehiclePage').then((m) => ({ default: m.GaVehiclePage })));
const GaMaintenanceReportPage = lazyPage(() => import('./pages/ga/GaMaintenanceReportPage').then((m) => ({ default: m.GaMaintenanceReportPage })));
const GaFleetReportPage = lazyPage(() => import('./pages/ga/GaFleetReportPage').then((m) => ({ default: m.GaFleetReportPage })));
const GaBuildingPage = lazyPage(() => import('./pages/ga/GaBuildingPage').then((m) => ({ default: m.GaBuildingPage })));
const GaEquipmentPage = lazyPage(() => import('./pages/ga/GaEquipmentPage').then((m) => ({ default: m.GaEquipmentPage })));
const GaMasterPage = lazyPage(() => import('./pages/ga/GaMasterPage').then((m) => ({ default: m.GaMasterPage })));
const CustomerDeposits = lazyPage(() => import('./pages/CustomerDeposits').then((m) => ({ default: m.CustomerDeposits })));
const FixedAssetsPage = lazyPage(() => import('./pages/FixedAssetsPage'));
const ApprovalsPage = lazyPage(() => import('./pages/ApprovalsPage'));
const DailyReport = lazyPage(() => import('./pages/reports/DailyReport'));
const OeeReport = lazyPage(() => import('./pages/reports/OeeReport'));
const LedgerBook = lazyPage(() => import('./pages/reports/LedgerBook'));
const HealthCheckPage = lazyPage(() => import('./pages/HealthCheckPage'));
const TaxReport = lazyPage(() => import('./pages/reports/TaxReport'));
const HppReport = lazyPage(() => import('./pages/reports/HppReport'));
const ProfitabilityPerSku = lazyPage(() => import('./pages/reports/ProfitabilityPerSku'));
const ProductionCostSummary = lazyPage(() => import('./pages/reports/ProductionCostSummary'));
const PayableSchedule = lazyPage(() => import('./pages/reports/PayableSchedule'));
const ReceivableSchedule = lazyPage(() => import('./pages/reports/ReceivableSchedule'));
const PoOutstanding = lazyPage(() => import('./pages/reports/PoOutstanding'));
const SupplierPerformance = lazyPage(() => import('./pages/reports/SupplierPerformance'));
const PaymentRequests = lazyPage(() => import('./pages/PaymentRequests').then((m) => ({ default: m.PaymentRequests })));
const PaymentRequestForm = lazyPage(() => import('./pages/PaymentRequestForm').then((m) => ({ default: m.PaymentRequestForm })));
const PaymentRequestSettlementPage = lazyPage(() => import('./pages/PaymentRequestSettlement').then((m) => ({ default: m.PaymentRequestSettlementPage })));
const PaymentRequestDetailPage = lazyPage(() => import('./pages/PaymentRequestDetail').then((m) => ({ default: m.PaymentRequestDetailPage })));
const CashBookDrafts = lazyPage(() => import('./pages/CashBookDrafts').then((m) => ({ default: m.CashBookDrafts })));
const CashBookDraftReview = lazyPage(() => import('./pages/CashBookDraftReview').then((m) => ({ default: m.CashBookDraftReview })));

/** Gate rute per izin modul (view); tanpa akses → dasbor ERP (selalu bisa dibuka). */
function Perm({ module, children }: { module: string; children: ReactNode }) {
  if (can(module, 'view')) return <>{children}</>;
  return <Navigate to="/dashboard/erp" replace />;
}

export default function ErpApp() {
  return (
    <ErpShell>
      <Routes>
        <Route element={<ErpLayout />}>
          <Route index element={<Dashboard />} />
          <Route path="payment-requests" element={<PaymentRequests />} />
          <Route path="payment-requests/new" element={<PaymentRequestForm />} />
          <Route path="payment-requests/:id/edit" element={<PaymentRequestForm />} />
          <Route path="payment-requests/:id/settlement" element={<PaymentRequestSettlementPage />} />
          <Route path="payment-requests/:id" element={<PaymentRequestDetailPage />} />
          <Route path="coa" element={<Perm module="coa"><COAPage /></Perm>} />
          <Route path="gl" element={<Perm module="journal"><GeneralLedger /></Perm>} />
          <Route path="sales" element={<Perm module="sales_invoice"><SalesInvoices /></Perm>} />
          <Route path="inquiries" element={<Perm module="quotation"><Inquiries /></Perm>} />
          <Route path="quotations" element={<Perm module="quotation"><Quotations /></Perm>} />
          <Route path="sales-orders" element={<Perm module="sales_order"><SalesOrders /></Perm>} />
          <Route path="sales-returns" element={<Perm module="sales_return"><SalesReturnsPage /></Perm>} />
          <Route path="purchase" element={<Perm module="purchase_invoice"><PurchaseInvoices /></Perm>} />
          <Route path="payments" element={<Perm module="payment"><Payments /></Perm>} />
          <Route path="cash-book-drafts" element={<Perm module="journal"><CashBookDrafts /></Perm>} />
          <Route path="cash-book-drafts/:id" element={<Perm module="journal"><CashBookDraftReview /></Perm>} />
          <Route path="vendor-deposits" element={<Perm module="vendor_deposit"><VendorDeposits /></Perm>} />
          <Route path="project-guarantees" element={<Perm module="project_guarantee"><ProjectGuarantees /></Perm>} />
          <Route path="ga" element={<Perm module="ga_assets"><GaDashboardPage /></Perm>} />
          <Route path="ga/assets" element={<Perm module="ga_assets"><GaAssetsPage /></Perm>} />
          <Route path="ga/documents" element={<Perm module="ga_documents"><GaDocumentsPage /></Perm>} />
          <Route path="ga/inventory" element={<Perm module="ga_inventory"><GaInventoryPage /></Perm>} />
          <Route path="ga/maintenance" element={<Perm module="ga_maintenance"><GaMaintenancePage /></Perm>} />
          <Route path="ga/maintenance/report" element={<Perm module="ga_maintenance"><GaMaintenanceReportPage /></Perm>} />
          <Route path="ga/material-requests" element={<Perm module="ga_material_request"><GaMaterialRequestPage /></Perm>} />
          <Route path="ga/vehicle" element={<Perm module="ga_vehicle"><GaVehiclePage /></Perm>} />
          <Route path="ga/vehicle/report" element={<Perm module="ga_vehicle"><GaFleetReportPage /></Perm>} />
          <Route path="ga/building" element={<Perm module="ga_building"><GaBuildingPage /></Perm>} />
          <Route path="ga/equipment" element={<Perm module="ga_equipment"><GaEquipmentPage /></Perm>} />
          <Route path="ga/master" element={<Perm module="ga_master"><GaMasterPage /></Perm>} />
          <Route path="ga/finance" element={<Navigate to="/dashboard/erp/payment-requests?tab=eam" replace />} />
          <Route path="customer-deposits" element={<Perm module="customer_deposit"><CustomerDeposits /></Perm>} />
          <Route path="parties" element={<Perm module="parties"><PartiesPage /></Perm>} />
          <Route path="inventory" element={<Perm module="inventory"><InventoryPage /></Perm>} />
          <Route path="stock-opname" element={<Perm module="stock_opname"><StockOpnamePage /></Perm>} />
          <Route path="warehouses" element={<Perm module="warehouse"><Warehouses /></Perm>} />
          <Route path="warehouse-stock" element={<Perm module="inventory"><WarehouseStockDashboard /></Perm>} />
          <Route path="toll-service" element={<Perm module="toll_service"><TollService /></Perm>} />
          <Route path="stock-transfers" element={<Perm module="stock_transfer"><StockTransfers /></Perm>} />
          <Route path="delivery-orders" element={<Perm module="delivery_order"><DeliveryOrders /></Perm>} />
          <Route path="goods-receipts" element={<Perm module="goods_receipt"><GoodsReceipts /></Perm>} />
          <Route path="purchase-orders" element={<Perm module="purchase_order"><PurchaseOrders /></Perm>} />
          <Route path="reports" element={<Perm module="reports"><Reports /></Perm>} />
          <Route path="reports/daily" element={<Perm module="reports"><DailyReport /></Perm>} />
          <Route path="reports/trial-balance" element={<Perm module="reports"><TrialBalance /></Perm>} />
          <Route path="reports/profit-loss" element={<Perm module="reports"><ProfitLoss /></Perm>} />
          <Route path="reports/balance-sheet" element={<Perm module="reports"><BalanceSheet /></Perm>} />
          <Route path="reports/cash-flow" element={<Perm module="reports"><CashFlow /></Perm>} />
          <Route path="reports/aging-ar" element={<Perm module="reports"><AgingAnalysis type="Customer" /></Perm>} />
          <Route path="reports/aging-ap" element={<Perm module="reports"><AgingAnalysis type="Supplier" /></Perm>} />
          <Route path="reports/tax" element={<Perm module="reports"><TaxReport /></Perm>} />
          <Route path="reports/ledger-book" element={<Perm module="reports"><LedgerBook /></Perm>} />
          <Route path="reports/hpp" element={<Perm module="reports"><HppReport /></Perm>} />
          <Route path="reports/profitability-per-sku" element={<Perm module="reports"><ProfitabilityPerSku /></Perm>} />
          <Route path="reports/production-cost-summary" element={<Perm module="reports"><ProductionCostSummary /></Perm>} />
          <Route path="reports/oee" element={<Perm module="reports"><OeeReport /></Perm>} />
          <Route path="reports/payable-schedule" element={<Perm module="reports"><PayableSchedule /></Perm>} />
          <Route path="reports/receivable-schedule" element={<Perm module="reports"><ReceivableSchedule /></Perm>} />
          <Route path="reports/po-outstanding" element={<Perm module="reports"><PoOutstanding /></Perm>} />
          <Route path="reports/supplier-performance" element={<Perm module="reports"><SupplierPerformance /></Perm>} />
          <Route path="reconciliation" element={<Perm module="reconciliation"><BankReconciliation /></Perm>} />
          <Route path="settings" element={<Perm module="settings"><SettingsPage /></Perm>} />
          <Route path="users" element={<Perm module="users"><UserManagement /></Perm>} />
          <Route path="audit" element={<Perm module="audit"><AuditTrail /></Perm>} />
          <Route path="health-check" element={<Perm module="audit"><HealthCheckPage /></Perm>} />
          <Route path="fixed-assets" element={<Perm module="fixed_assets"><FixedAssetsPage /></Perm>} />
          <Route path="approvals" element={<Perm module="approvals"><ApprovalsPage /></Perm>} />
          <Route path="notifications" element={<Notifications />} />
          <Route path="recurring" element={<Perm module="recurring"><RecurringTransactions /></Perm>} />
          <Route path="*" element={<Navigate to="/dashboard/erp" replace />} />
        </Route>
      </Routes>
    </ErpShell>
  );
}
