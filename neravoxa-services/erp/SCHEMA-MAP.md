# Peta Skema ERP (Prisma ENTARIS -> db_erp / JPA)

Dibangkitkan otomatis. Nama field Java/JSON = nama field Prisma; kolom DB = snake_case.
Relasi `@ManyToOne` bersifat read-only (`insertable=false, updatable=false`) — tulis lewat field FK skalar (mis. `partyId`).
Sisi balik relasi 1:1 dipetakan sebagai `List<X> <nama>Ref` (pakai untuk JOIN FETCH), JSON tetap `<nama>`.

## Account -> `accounts`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| name | String | `name` |
| accountNumber | String | `account_number` |
| accountType | AccountType | `account_type` |
| rootType | RootType | `root_type` |
| isGroup | Boolean | `is_group` |
| isActive | Boolean | `is_active` |
| balance | Decimal | `balance` |
| currency | String | `currency` |
| description | String? | `description` |
| parentId | String? | `parent_id` |
| parent | Account | -> Account via `parent_id` |
| children | Account[] | <- Account.parent (list) |
| ledgerEntries | AccountingLedgerEntry[] | <- AccountingLedgerEntry.account (list) |
| journalItems | JournalItem[] | <- JournalItem.account (list) |
| salesItems | SalesInvoiceItem[] | <- SalesInvoiceItem.account (list) |
| purchaseItems | PurchaseInvoiceItem[] | <- PurchaseInvoiceItem.account (list) |
| quotationItems | QuotationItem[] | <- QuotationItem.account (list) |
| payments | Payment[] | <- Payment.account (list) |
| paymentSplits | PaymentSplit[] | <- PaymentSplit.account (list) |
| inventoryItems | InventoryItem[] | <- InventoryItem.account (list) |
| movementsAsOffset | StockMovement[] | <- StockMovement.offsetAccount (list) |
| reconciliations | BankReconciliation[] | <- BankReconciliation.account (list) |
| serviceItems | ServiceItem[] | <- ServiceItem.account (list) |
| systemAccountMappings | SystemAccountMapping[] | <- SystemAccountMapping.account (list) |
| fixedAssetsAsAsset | FixedAsset[] | <- FixedAsset.assetAccount (list) |
| fixedAssetsAsDepr | FixedAsset[] | <- FixedAsset.depreciationAccount (list) |
| fixedAssetsAsAccDep | FixedAsset[] | <- FixedAsset.accumulatedDepAccount (list) |
| productionCostStandards | ProductionCostStandard[] | <- ProductionCostStandard.costAccount (list) |
| productionRunCosts | ProductionRunCost[] | <- ProductionRunCost.costAccount (list) |
| gaVehicleServiceExpense | GaVehicleServiceLog[] | <- GaVehicleServiceLog.account (list) |
| gaVehicleTaxExpense | GaVehicleTax[] | <- GaVehicleTax.account (list) |
| gaVehicleTaxCredit | GaVehicleTax[] | <- GaVehicleTax.creditAccount (list) |
| gaMaterialRequestExpense | GaMaterialRequest[] | <- GaMaterialRequest.glAccount (list) |
| gaMaterialRequestCredit | GaMaterialRequest[] | <- GaMaterialRequest.creditAccount (list) |
| gaVehicleServiceCredit | GaVehicleServiceLog[] | <- GaVehicleServiceLog.creditAccount (list) |
| paymentRequestTypes | PaymentRequestType[] | <- PaymentRequestType.defaultExpenseAccount (list) |
| paymentRequestLines | PaymentRequestLine[] | <- PaymentRequestLine.expenseAccount (list) |
| paymentRequestsPaidFrom | PaymentRequest[] | <- PaymentRequest.cashAccount (list) |
| paymentRequestsSettledFrom | PaymentRequest[] | <- PaymentRequest.settlementCashAccount (list) |
| paymentRequestSettlementLines | PaymentRequestSettlementLine[] | <- PaymentRequestSettlementLine.expenseAccount (list) |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## Party -> `parties`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| name | String | `name` |
| partyType | PartyType | `party_type` |
| phone | String? | `phone` |
| email | String? | `email` |
| address | String? | `address` |
| taxId | String? | `tax_id` |
| outstandingAmount | Decimal | `outstanding_amount` |
| openingOutstanding | Decimal | `opening_outstanding` |
| depositBalance | Decimal | `deposit_balance` |
| customerDepositBalance | Decimal | `customer_deposit_balance` |
| isActive | Boolean | `is_active` |
| isDummy | Boolean | `is_dummy` |
| isWapu | Boolean | `is_wapu` |
| salesInvoices | SalesInvoice[] | <- SalesInvoice.customer (list) |
| purchaseInvoices | PurchaseInvoice[] | <- PurchaseInvoice.supplier (list) |
| payments | Payment[] | <- Payment.party (list) |
| quotations | Quotation[] | <- Quotation.customer (list) |
| inquiries | Inquiry[] | <- Inquiry.party (list) |
| deliveryOrders | DeliveryOrder[] | <- DeliveryOrder.customer (list) |
| salesOrders | SalesOrder[] | <- SalesOrder.customer (list) |
| purchaseOrders | PurchaseOrder[] | <- PurchaseOrder.supplier (list) |
| goodsReceipts | GoodsReceipt[] | <- GoodsReceipt.supplier (list) |
| salesReturns | SalesReturn[] | <- SalesReturn.customer (list) |
| projectGuarantees | ProjectGuarantee[] | <- ProjectGuarantee.party (list) |
| ledgerEntries | AccountingLedgerEntry[] | <- AccountingLedgerEntry.party (list) |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |
| paymentRequests | PaymentRequest[] | <- PaymentRequest.party (list) |

## AccountingLedgerEntry -> `accounting_ledger_entries`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| date | DateTime | `date` |
| accountId | String | `account_id` |
| account | Account | -> Account via `account_id` |
| partyId | String? | `party_id` |
| party | Party | -> Party via `party_id` |
| debit | Decimal | `debit` |
| credit | Decimal | `credit` |
| referenceType | String | `reference_type` |
| referenceId | String | `reference_id` |
| description | String? | `description` |
| fiscalYearId | String | `fiscal_year_id` |
| fiscalYear | FiscalYear | -> FiscalYear via `fiscal_year_id` |
| isCancelled | Boolean | `is_cancelled` |
| createdAt | DateTime | `created_at` |

## JournalEntry -> `journal_entries`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| entryNumber | String | `entry_number` |
| date | DateTime | `date` |
| status | DocumentStatus | `status` |
| narration | String? | `narration` |
| fiscalYearId | String | `fiscal_year_id` |
| fiscalYear | FiscalYear | -> FiscalYear via `fiscal_year_id` |
| createdBy | String | `created_by` |
| user | User | -> User via `created_by` |
| items | JournalItem[] | <- JournalItem.journalEntry (list) |
| stockMovement | StockMovement | <- StockMovement.journalEntry (1:1, field `stockMovementRef`) |
| payment | Payment | <- Payment.journalEntry (1:1, field `paymentRef`) |
| depositApplication | VendorDepositApplication | <- VendorDepositApplication.journalEntry (1:1, field `depositApplicationRef`) |
| customerDepositApplication | CustomerDepositApplication | <- CustomerDepositApplication.journalEntry (1:1, field `customerDepositApplicationRef`) |
| depreciationEntries | DepreciationEntry[] | <- DepreciationEntry.journalEntry (list) |
| cogsBackfillSettlements | CogsBackfillSettlement[] | <- CogsBackfillSettlement.journalEntry (list) |
| gaVehicleServiceLog | GaVehicleServiceLog | <- GaVehicleServiceLog.journalEntry (1:1, field `gaVehicleServiceLogRef`) |
| gaVehicleTax | GaVehicleTax | <- GaVehicleTax.journalEntry (1:1, field `gaVehicleTaxRef`) |
| gaMaterialRequest | GaMaterialRequest | <- GaMaterialRequest.journalEntry (1:1, field `gaMaterialRequestRef`) |
| paymentRequest | PaymentRequest | <- PaymentRequest.journalEntry (1:1, field `paymentRequestRef`) |
| paymentRequestSettlement | PaymentRequest | <- PaymentRequest.settlementJournal (1:1, field `paymentRequestSettlementRef`) |
| submittedAt | DateTime? | `submitted_at` |
| isDummy | Boolean | `is_dummy` |
| cancelledAt | DateTime? | `cancelled_at` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## JournalItem -> `journal_entry_accounts`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| journalEntryId | String | `journal_entry_id` |
| journalEntry | JournalEntry | -> JournalEntry via `journal_entry_id` |
| accountId | String | `account_id` |
| account | Account | -> Account via `account_id` |
| partyId | String? | `party_id` |
| debit | Decimal | `debit` |
| credit | Decimal | `credit` |
| description | String? | `description` |

## SalesInvoice -> `sales_invoices`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| invoiceNumber | String | `invoice_number` |
| date | DateTime | `date` |
| partyId | String | `party_id` |
| customer | Party | -> Party via `party_id` |
| status | InvoiceStatus | `status` |
| grandTotal | Decimal | `grand_total` |
| outstanding | Decimal | `outstanding` |
| taxPct | Decimal | `tax_pct` |
| potongan | Decimal | `potongan` |
| biayaLain | Decimal | `biaya_lain` |
| labelPotongan | String? | `label_potongan` |
| labelBiaya | String? | `label_biaya` |
| terms | String? | `terms` |
| dueDate | DateTime? | `due_date` |
| notes | String? | `notes` |
| customerRefNo | String? | `customer_ref_no` |
| fiscalYearId | String | `fiscal_year_id` |
| fiscalYear | FiscalYear | -> FiscalYear via `fiscal_year_id` |
| createdBy | String | `created_by` |
| user | User | -> User via `created_by` |
| items | SalesInvoiceItem[] | <- SalesInvoiceItem.salesInvoice (list) |
| cogsBackfills | CogsBackfillQueue[] | <- CogsBackfillQueue.salesInvoice (list) |
| depositApplications | CustomerDepositApplication[] | <- CustomerDepositApplication.salesInvoice (list) |
| quotation | Quotation | <- Quotation.salesInvoice (1:1, field `quotationRef`) |
| deliveryOrders | DeliveryOrder[] | <- DeliveryOrder.invoice (list) |
| returns | SalesReturn[] | <- SalesReturn.salesInvoice (list) |
| submittedAt | DateTime? | `submitted_at` |
| cancelledAt | DateTime? | `cancelled_at` |
| isDummy | Boolean | `is_dummy` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## SalesOrder -> `sales_orders`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| soNumber | String | `so_number` |
| date | DateTime | `date` |
| expectedDelivery | DateTime? | `expected_delivery` |
| partyId | String | `party_id` |
| customer | Party | -> Party via `party_id` |
| customerRefNo | String? | `customer_ref_no` |
| quotationId | String? | `quotation_id` |
| quotation | Quotation | -> Quotation via `quotation_id` |
| status | SalesOrderStatus | `status` |
| notes | String? | `notes` |
| subtotal | Decimal | `subtotal` |
| taxPct | Decimal | `tax_pct` |
| potongan | Decimal | `potongan` |
| biayaLain | Decimal | `biaya_lain` |
| grandTotal | Decimal | `grand_total` |
| fiscalYearId | String | `fiscal_year_id` |
| fiscalYear | FiscalYear | -> FiscalYear via `fiscal_year_id` |
| createdBy | String | `created_by` |
| user | User | -> User via `created_by` |
| items | SalesOrderItem[] | <- SalesOrderItem.salesOrder (list) |
| deliveryOrders | DeliveryOrder[] | <- DeliveryOrder.salesOrder (list) |
| depositPayments | Payment[] | <- Payment.salesOrder (list) |
| submittedAt | DateTime? | `submitted_at` |
| cancelledAt | DateTime? | `cancelled_at` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## SalesOrderItem -> `sales_order_items`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| salesOrderId | String | `sales_order_id` |
| salesOrder | SalesOrder | -> SalesOrder via `sales_order_id` |
| inventoryItemId | String? | `inventory_item_id` |
| inventoryItem | InventoryItem | -> InventoryItem via `inventory_item_id` |
| itemName | String | `item_name` |
| quantity | Decimal | `quantity` |
| unit | String | `unit` |
| rate | Decimal | `rate` |
| discount | Decimal | `discount` |
| amount | Decimal | `amount` |
| description | String? | `description` |
| itemType | String | `item_type` |
| serviceItemId | String? | `service_item_id` |
| deliveredQty | Decimal | `delivered_qty` |
| deliveryOrderItems | DeliveryOrderItem[] | <- DeliveryOrderItem.salesOrderItem (list) |
| createdAt | DateTime | `created_at` |

## Quotation -> `quotations`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| quotationNumber | String | `quotation_number` |
| date | DateTime | `date` |
| validUntil | DateTime | `valid_until` |
| partyId | String | `party_id` |
| customer | Party | -> Party via `party_id` |
| status | QuotationStatus | `status` |
| subtotal | Decimal | `subtotal` |
| taxPct | Decimal | `tax_pct` |
| potongan | Decimal | `potongan` |
| biayaLain | Decimal | `biaya_lain` |
| labelPotongan | String? | `label_potongan` |
| labelBiaya | String? | `label_biaya` |
| grandTotal | Decimal | `grand_total` |
| terms | String? | `terms` |
| notes | String? | `notes` |
| rejectReason | String? | `reject_reason` |
| salesInvoiceId | String? | `sales_invoice_id` |
| salesInvoice | SalesInvoice | -> SalesInvoice via `sales_invoice_id` |
| salesOrders | SalesOrder[] | <- SalesOrder.quotation (list) |
| inquiry | Inquiry | <- Inquiry.quotation (1:1, field `inquiryRef`) |
| fiscalYearId | String | `fiscal_year_id` |
| fiscalYear | FiscalYear | -> FiscalYear via `fiscal_year_id` |
| createdBy | String | `created_by` |
| user | User | -> User via `created_by` |
| items | QuotationItem[] | <- QuotationItem.quotation (list) |
| sentAt | DateTime? | `sent_at` |
| acceptedAt | DateTime? | `accepted_at` |
| rejectedAt | DateTime? | `rejected_at` |
| expiredAt | DateTime? | `expired_at` |
| convertedAt | DateTime? | `converted_at` |
| cancelledAt | DateTime? | `cancelled_at` |
| expiryNotifiedAt | DateTime? | `expiry_notified_at` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## QuotationItem -> `quotation_items`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| quotationId | String | `quotation_id` |
| quotation | Quotation | -> Quotation via `quotation_id` |
| itemType | String | `item_type` |
| inventoryItemId | String? | `inventory_item_id` |
| inventoryItem | InventoryItem | -> InventoryItem via `inventory_item_id` |
| serviceItemId | String? | `service_item_id` |
| serviceItem | ServiceItem | -> ServiceItem via `service_item_id` |
| accountId | String? | `account_id` |
| account | Account | -> Account via `account_id` |
| itemName | String | `item_name` |
| quantity | Decimal | `quantity` |
| unit | String | `unit` |
| rate | Decimal | `rate` |
| discount | Decimal | `discount` |
| amount | Decimal | `amount` |
| taxPct | Decimal | `tax_pct` |
| pphPct | Decimal | `pph_pct` |
| description | String? | `description` |
| createdAt | DateTime | `created_at` |

## Inquiry -> `inquiries`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| inquiryNumber | String | `inquiry_number` |
| date | DateTime | `date` |
| status | InquiryStatus | `status` |
| channel | InquiryChannel | `channel` |
| partyId | String? | `party_id` |
| party | Party | -> Party via `party_id` |
| prospectName | String | `prospect_name` |
| prospectPhone | String? | `prospect_phone` |
| prospectEmail | String? | `prospect_email` |
| itemsMode | String | `items_mode` |
| itemsText | String? | `items_text` |
| items | InquiryItem[] | <- InquiryItem.inquiry (list) |
| notes | String? | `notes` |
| followUpDate | DateTime? | `follow_up_date` |
| lossReason | String? | `loss_reason` |
| quotationId | String? | `quotation_id` |
| quotation | Quotation | -> Quotation via `quotation_id` |
| convertedAt | DateTime? | `converted_at` |
| fiscalYearId | String | `fiscal_year_id` |
| fiscalYear | FiscalYear | -> FiscalYear via `fiscal_year_id` |
| createdBy | String | `created_by` |
| user | User | -> User via `created_by` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## InquiryItem -> `inquiry_items`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| inquiryId | String | `inquiry_id` |
| inquiry | Inquiry | -> Inquiry via `inquiry_id` |
| itemName | String | `item_name` |
| quantity | Decimal? | `quantity` |
| unit | String? | `unit` |
| estimatedPrice | Decimal? | `estimated_price` |
| description | String? | `description` |
| createdAt | DateTime | `created_at` |

## SalesInvoiceItem -> `sales_invoice_items`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| salesInvoiceId | String | `sales_invoice_id` |
| salesInvoice | SalesInvoice | -> SalesInvoice via `sales_invoice_id` |
| itemType | String | `item_type` |
| inventoryItemId | String? | `inventory_item_id` |
| inventoryItem | InventoryItem | -> InventoryItem via `inventory_item_id` |
| warehouseId | String? | `warehouse_id` |
| warehouse | Warehouse | -> Warehouse via `warehouse_id` |
| serviceItemId | String? | `service_item_id` |
| serviceItem | ServiceItem | -> ServiceItem via `service_item_id` |
| deliveryOrderItemId | String? | `delivery_order_item_id` |
| deliveryOrderItem | DeliveryOrderItem | -> DeliveryOrderItem via `delivery_order_item_id` |
| cogsBackfills | CogsBackfillQueue[] | <- CogsBackfillQueue.salesInvoiceItem (list) |
| returnItems | SalesReturnItem[] | <- SalesReturnItem.salesInvoiceItem (list) |
| itemName | String | `item_name` |
| quantity | Decimal | `quantity` |
| unit | String | `unit` |
| rate | Decimal | `rate` |
| discount | Decimal | `discount` |
| amount | Decimal | `amount` |
| taxPct | Decimal | `tax_pct` |
| pphPct | Decimal | `pph_pct` |
| accountId | String | `account_id` |
| account | Account | -> Account via `account_id` |
| description | String? | `description` |
| sourceMovementId | String? | `source_movement_id` |
| tollOrderId | String? | `toll_order_id` |
| tollChargeType | String? | `toll_charge_type` |
| salesOrderItemId | String? | `sales_order_item_id` |
| sourceMovement | StockMovement | -> StockMovement via `source_movement_id` |
| unitCostLoaded | Decimal? | `unit_cost_loaded` |

## DeliveryOrder -> `delivery_orders`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| doNumber | String | `do_number` |
| date | DateTime | `date` |
| partyId | String | `party_id` |
| customer | Party | -> Party via `party_id` |
| status | DocumentStatus | `status` |
| notes | String? | `notes` |
| nomorMobil | String? | `nomor_mobil` |
| driverName | String? | `driver_name` |
| recipientName | String? | `recipient_name` |
| invoiceId | String? | `invoice_id` |
| invoice | SalesInvoice | -> SalesInvoice via `invoice_id` |
| invoicedAt | DateTime? | `invoiced_at` |
| salesOrderId | String? | `sales_order_id` |
| salesOrder | SalesOrder | -> SalesOrder via `sales_order_id` |
| fiscalYearId | String | `fiscal_year_id` |
| fiscalYear | FiscalYear | -> FiscalYear via `fiscal_year_id` |
| createdBy | String | `created_by` |
| user | User | -> User via `created_by` |
| items | DeliveryOrderItem[] | <- DeliveryOrderItem.deliveryOrder (list) |
| submittedAt | DateTime? | `submitted_at` |
| cancelledAt | DateTime? | `cancelled_at` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## DeliveryOrderItem -> `delivery_order_items`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| deliveryOrderId | String | `delivery_order_id` |
| deliveryOrder | DeliveryOrder | -> DeliveryOrder via `delivery_order_id` |
| inventoryItemId | String? | `inventory_item_id` |
| inventoryItem | InventoryItem | -> InventoryItem via `inventory_item_id` |
| warehouseId | String? | `warehouse_id` |
| warehouse | Warehouse | -> Warehouse via `warehouse_id` |
| itemName | String | `item_name` |
| quantity | Decimal | `quantity` |
| unit | String | `unit` |
| description | String? | `description` |
| stockMovementId | String? | `stock_movement_id` |
| stockMovement | StockMovement | -> StockMovement via `stock_movement_id` |
| salesOrderItemId | String? | `sales_order_item_id` |
| salesOrderItem | SalesOrderItem | -> SalesOrderItem via `sales_order_item_id` |
| invoiceItem | SalesInvoiceItem | <- SalesInvoiceItem.deliveryOrderItem (1:1, field `invoiceItemRef`) |
| createdAt | DateTime | `created_at` |

## PurchaseInvoice -> `purchase_invoices`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| invoiceNumber | String | `invoice_number` |
| date | DateTime | `date` |
| partyId | String | `party_id` |
| supplier | Party | -> Party via `party_id` |
| status | InvoiceStatus | `status` |
| grandTotal | Decimal | `grand_total` |
| outstanding | Decimal | `outstanding` |
| taxPct | Decimal | `tax_pct` |
| potongan | Decimal | `potongan` |
| biayaLain | Decimal | `biaya_lain` |
| dueDate | DateTime? | `due_date` |
| notes | String? | `notes` |
| terms | String? | `terms` |
| fiscalYearId | String | `fiscal_year_id` |
| fiscalYear | FiscalYear | -> FiscalYear via `fiscal_year_id` |
| createdBy | String | `created_by` |
| user | User | -> User via `created_by` |
| items | PurchaseInvoiceItem[] | <- PurchaseInvoiceItem.purchaseInvoice (list) |
| depositApplications | VendorDepositApplication[] | <- VendorDepositApplication.purchaseInvoice (list) |
| purchaseOrderId | String? | `purchase_order_id` |
| purchaseOrder | PurchaseOrder | -> PurchaseOrder via `purchase_order_id` |
| goodsReceipts | GoodsReceipt[] | <- GoodsReceipt.invoice (list) |
| gaVehicleTaxes | GaVehicleTax[] | <- GaVehicleTax.purchaseInvoice (list) |
| gaServiceLogs | GaVehicleServiceLog[] | <- GaVehicleServiceLog.purchaseInvoice (list) |
| gaMaterialRequests | GaMaterialRequest[] | <- GaMaterialRequest.purchaseInvoice (list) |
| isLegacyStockFlow | Boolean | `is_legacy_stock_flow` |
| submittedAt | DateTime? | `submitted_at` |
| cancelledAt | DateTime? | `cancelled_at` |
| isDummy | Boolean | `is_dummy` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## PurchaseInvoiceItem -> `purchase_invoice_items`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| purchaseInvoiceId | String | `purchase_invoice_id` |
| purchaseInvoice | PurchaseInvoice | -> PurchaseInvoice via `purchase_invoice_id` |
| itemType | String | `item_type` |
| inventoryItemId | String? | `inventory_item_id` |
| inventoryItem | InventoryItem | -> InventoryItem via `inventory_item_id` |
| warehouseId | String? | `warehouse_id` |
| warehouse | Warehouse | -> Warehouse via `warehouse_id` |
| itemName | String | `item_name` |
| quantity | Decimal | `quantity` |
| unit | String | `unit` |
| rate | Decimal | `rate` |
| discount | Decimal | `discount` |
| amount | Decimal | `amount` |
| taxPct | Decimal | `tax_pct` |
| pphPct | Decimal | `pph_pct` |
| potonganItem | Decimal | `potongan_item` |
| kualitas | String? | `kualitas` |
| refaksi | Decimal? | `refaksi` |
| timbanganTruk | Decimal? | `timbangan_truk` |
| timbanganDiterima | Decimal? | `timbangan_diterima` |
| nomorMobil | String? | `nomor_mobil` |
| accountId | String | `account_id` |
| account | Account | -> Account via `account_id` |
| description | String? | `description` |
| goodsReceiptItemId | String? | `goods_receipt_item_id` |
| goodsReceiptItem | GoodsReceiptItem | -> GoodsReceiptItem via `goods_receipt_item_id` |

## Payment -> `payments`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| paymentNumber | String | `payment_number` |
| date | DateTime | `date` |
| paymentType | PaymentType | `payment_type` |
| partyId | String | `party_id` |
| party | Party | -> Party via `party_id` |
| accountId | String | `account_id` |
| account | Account | -> Account via `account_id` |
| amount | Decimal | `amount` |
| refundedAmount | Decimal | `refunded_amount` |
| status | DocumentStatus | `status` |
| referenceNo | String? | `reference_number` |
| notes | String? | `notes` |
| createdBy | String | `created_by` |
| user | User | -> User via `created_by` |
| allocations | PaymentAllocation[] | <- PaymentAllocation.payment (list) |
| splits | PaymentSplit[] | <- PaymentSplit.payment (list) |
| depositApplications | VendorDepositApplication[] | <- VendorDepositApplication.depositPayment (list) |
| customerDepositApplications | CustomerDepositApplication[] | <- CustomerDepositApplication.depositPayment (list) |
| journalEntryId | String? | `journal_entry_id` |
| journalEntry | JournalEntry | -> JournalEntry via `journal_entry_id` |
| salesOrderId | String? | `sales_order_id` |
| salesOrder | SalesOrder | -> SalesOrder via `sales_order_id` |
| fiscalYearId | String | `fiscal_year_id` |
| fiscalYear | FiscalYear | -> FiscalYear via `fiscal_year_id` |
| submittedAt | DateTime? | `submitted_at` |
| cancelledAt | DateTime? | `cancelled_at` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## PaymentSplit -> `payment_splits`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| paymentId | String | `payment_id` |
| payment | Payment | -> Payment via `payment_id` |
| accountId | String | `account_id` |
| account | Account | -> Account via `account_id` |
| amount | Decimal | `amount` |
| notes | String? | `notes` |

## PaymentAllocation -> `payment_allocations`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| paymentId | String | `payment_id` |
| payment | Payment | -> Payment via `payment_id` |
| invoiceType | String | `invoice_type` |
| invoiceId | String | `invoice_id` |
| allocatedAmount | Decimal | `allocated_amount` |

## FiscalYear -> `fiscal_years`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| name | String | `name` |
| startDate | DateTime | `start_date` |
| endDate | DateTime | `end_date` |
| isClosed | Boolean | `is_closed` |
| closedAt | DateTime? | `closed_at` |
| closedBy | String? | `closed_by` |
| user | User | -> User via `closed_by` |
| ledgerEntries | AccountingLedgerEntry[] | <- AccountingLedgerEntry.fiscalYear (list) |
| journalEntries | JournalEntry[] | <- JournalEntry.fiscalYear (list) |
| salesInvoices | SalesInvoice[] | <- SalesInvoice.fiscalYear (list) |
| purchaseInvoices | PurchaseInvoice[] | <- PurchaseInvoice.fiscalYear (list) |
| quotations | Quotation[] | <- Quotation.fiscalYear (list) |
| inquiries | Inquiry[] | <- Inquiry.fiscalYear (list) |
| payments | Payment[] | <- Payment.fiscalYear (list) |
| stockMovements | StockMovement[] | <- StockMovement.fiscalYear (list) |
| productionRuns | ProductionRun[] | <- ProductionRun.fiscalYear (list) |
| stockOpnames | StockOpname[] | <- StockOpname.fiscalYear (list) |
| stockTransfers | StockTransfer[] | <- StockTransfer.fiscalYear (list) |
| deliveryOrders | DeliveryOrder[] | <- DeliveryOrder.fiscalYear (list) |
| salesOrders | SalesOrder[] | <- SalesOrder.fiscalYear (list) |
| purchaseOrders | PurchaseOrder[] | <- PurchaseOrder.fiscalYear (list) |
| goodsReceipts | GoodsReceipt[] | <- GoodsReceipt.fiscalYear (list) |
| fixedAssets | FixedAsset[] | <- FixedAsset.fiscalYear (list) |
| cogsBackfills | CogsBackfillQueue[] | <- CogsBackfillQueue.fiscalYear (list) |
| createdAt | DateTime | `created_at` |

## User -> `users`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| username | String | `username` |
| fullName | String | `full_name` |
| email | String | `email` |
| npk | String? | `npk` |
| platformUserId | BigInt? | `platform_user_id` |
| roles | String? | `roles` |
| role | String | `role` |
| isActive | Boolean | `is_active` |
| lastLoginAt | DateTime? | `last_login_at` |
| language | String? | `language` |
| journalEntries | JournalEntry[] | <- JournalEntry.user (list) |
| salesInvoices | SalesInvoice[] | <- SalesInvoice.user (list) |
| purchaseInvoices | PurchaseInvoice[] | <- PurchaseInvoice.user (list) |
| quotations | Quotation[] | <- Quotation.user (list) |
| inquiries | Inquiry[] | <- Inquiry.user (list) |
| payments | Payment[] | <- Payment.user (list) |
| closedYears | FiscalYear[] | <- FiscalYear.user (list) |
| audits | AuditLog[] | <- AuditLog.user (list) |
| stockMovements | StockMovement[] | <- StockMovement.createdBy (list) |
| productionRuns | ProductionRun[] | <- ProductionRun.createdBy (list) |
| notifications | Notification[] | <- Notification.user (list) |
| recurringTemplates | RecurringTemplate[] | <- RecurringTemplate.user (list) |
| vendorDepositApplications | VendorDepositApplication[] | <- VendorDepositApplication.user (list) |
| customerDepositApplications | CustomerDepositApplication[] | <- CustomerDepositApplication.user (list) |
| attachments | TransactionAttachment[] | <- TransactionAttachment.user (list) |
| stockOpnames | StockOpname[] | <- StockOpname.createdBy (list) |
| stockTransfers | StockTransfer[] | <- StockTransfer.user (list) |
| deliveryOrders | DeliveryOrder[] | <- DeliveryOrder.user (list) |
| salesOrders | SalesOrder[] | <- SalesOrder.user (list) |
| purchaseOrders | PurchaseOrder[] | <- PurchaseOrder.user (list) |
| goodsReceipts | GoodsReceipt[] | <- GoodsReceipt.user (list) |
| fixedAssets | FixedAsset[] | <- FixedAsset.user (list) |
| approvalRequests | ApprovalRequest[] | <- ApprovalRequest.requester (list) |
| approvalApprovals | ApprovalRequest[] | <- ApprovalRequest.approver (list) |
| approvalRejections | ApprovalRequest[] | <- ApprovalRequest.rejecter (list) |
| supervisorId | String? | `supervisor_id` |
| supervisor | User | -> User via `supervisor_id` |
| subordinates | User[] | <- User.supervisor (list) |
| paymentRequests | PaymentRequest[] | <- PaymentRequest.requester (list) |
| paymentRequestsSupervised | PaymentRequest[] | <- PaymentRequest.supervisor (list) |
| paymentRequestSettlementsSupervised | PaymentRequest[] | <- PaymentRequest.settlementSupervisor (list) |
| paymentRequestsPaid | PaymentRequest[] | <- PaymentRequest.paidBy (list) |
| paymentRequestHistory | PaymentRequestHistory[] | <- PaymentRequestHistory.actor (list) |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## Role -> `roles`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| key | String | `key` |
| name | String | `name` |
| description | String? | `description` |
| isSystem | Boolean | `is_system` |
| isSuperAdmin | Boolean | `is_super_admin` |
| isActive | Boolean | `is_active` |
| permissions | RolePermission[] | <- RolePermission.role (list) |
| capabilities | RoleCapability[] | <- RoleCapability.role (list) |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## RolePermission -> `role_permissions`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| roleId | String | `role_id` |
| role | Role | -> Role via `role_id` |
| module | String | `module` |
| canView | Boolean | `can_view` |
| canEdit | Boolean | `can_edit` |

## RoleCapability -> `role_capabilities`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| roleId | String | `role_id` |
| role | Role | -> Role via `role_id` |
| capability | String | `capability` |

## AuditLog -> `audit_trail`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| userId | String | `user_id` |
| user | User | -> User via `user_id` |
| action | String | `action` |
| entityType | String | `entity_type` |
| entityId | String | `entity_id` |
| oldValues | Json? | `old_values` |
| newValues | Json? | `new_values` |
| ipAddress | String? | `ip_address` |
| userAgent | String? | `user_agent` |
| via | String? | `via` |
| viaMeta | Json? | `via_meta` |
| createdAt | DateTime | `created_at` |

## CompanySettings -> `company_settings`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| slug | String | `slug` |
| companyName | String | `company_name` |
| address | String? | `address` |
| phone | String? | `phone` |
| email | String? | `email` |
| taxId | String? | `tax_id` |
| logoUrl | String? | `logo_url` |
| logoUrlDark | String? | `logo_url_dark` |
| defaultCurrency | String | `default_currency` |
| fiscalYearStartMonth | Int | `fiscal_year_start_month` |
| defaultLanguage | String | `default_language` |
| invoiceSettings | Json? | `invoice_settings` |
| onboardedAt | DateTime? | `onboarded_at` |

## SystemAccountMapping -> `system_account_mappings`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| role | String | `role` |
| accountId | String | `account_id` |
| account | Account | -> Account via `account_id` |
| sortOrder | Int | `sort_order` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## Warehouse -> `warehouses`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| code | String | `code` |
| name | String | `name` |
| location | String? | `location` |
| description | String? | `description` |
| isActive | Boolean | `is_active` |
| isDefault | Boolean | `is_default` |
| sortOrder | Int | `sort_order` |
| stocks | InventoryStock[] | <- InventoryStock.warehouse (list) |
| movements | StockMovement[] | <- StockMovement.warehouse (list) |
| transfersFrom | StockTransfer[] | <- StockTransfer.fromWarehouse (list) |
| transfersTo | StockTransfer[] | <- StockTransfer.toWarehouse (list) |
| purchaseInvoiceItems | PurchaseInvoiceItem[] | <- PurchaseInvoiceItem.warehouse (list) |
| salesInvoiceItems | SalesInvoiceItem[] | <- SalesInvoiceItem.warehouse (list) |
| productionLines | ProductionRunItem[] | <- ProductionRunItem.warehouse (list) |
| opnameLines | StockOpnameItem[] | <- StockOpnameItem.warehouse (list) |
| opnames | StockOpname[] | <- StockOpname.warehouse (list) |
| deliveryOrderItems | DeliveryOrderItem[] | <- DeliveryOrderItem.warehouse (list) |
| goodsReceiptItems | GoodsReceiptItem[] | <- GoodsReceiptItem.warehouse (list) |
| costStandards | ProductionCostStandard[] | <- ProductionCostStandard.warehouse (list) |
| purchaseOrders | PurchaseOrder[] | <- PurchaseOrder.warehouse (list) |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## InventoryStock -> `inventory_stocks`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| itemId | String | `item_id` |
| item | InventoryItem | -> InventoryItem via `item_id` |
| warehouseId | String | `warehouse_id` |
| warehouse | Warehouse | -> Warehouse via `warehouse_id` |
| currentStock | Decimal | `current_stock` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## InventoryItem -> `inventory_items`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| code | String | `code` |
| name | String | `name` |
| unit | String | `unit` |
| category | String? | `category` |
| description | String? | `description` |
| currentStock | Decimal | `current_stock` |
| averageCost | Decimal | `average_cost` |
| minimumStock | Decimal | `minimum_stock` |
| isActive | Boolean | `is_active` |
| isDummy | Boolean | `is_dummy` |
| accountId | String? | `account_id` |
| account | Account | -> Account via `account_id` |
| stocks | InventoryStock[] | <- InventoryStock.item (list) |
| movements | StockMovement[] | <- StockMovement.item (list) |
| productionLines | ProductionRunItem[] | <- ProductionRunItem.item (list) |
| salesInvoiceItems | SalesInvoiceItem[] | <- SalesInvoiceItem.inventoryItem (list) |
| purchaseInvoiceItems | PurchaseInvoiceItem[] | <- PurchaseInvoiceItem.inventoryItem (list) |
| quotationItems | QuotationItem[] | <- QuotationItem.inventoryItem (list) |
| opnameLines | StockOpnameItem[] | <- StockOpnameItem.item (list) |
| cogsBackfills | CogsBackfillQueue[] | <- CogsBackfillQueue.inventoryItem (list) |
| transferLines | StockTransferItem[] | <- StockTransferItem.item (list) |
| deliveryOrderItems | DeliveryOrderItem[] | <- DeliveryOrderItem.inventoryItem (list) |
| salesOrderItems | SalesOrderItem[] | <- SalesOrderItem.inventoryItem (list) |
| purchaseOrderItems | PurchaseOrderItem[] | <- PurchaseOrderItem.inventoryItem (list) |
| goodsReceiptItems | GoodsReceiptItem[] | <- GoodsReceiptItem.inventoryItem (list) |
| costStandards | ProductionCostStandard[] | <- ProductionCostStandard.item (list) |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## StockTransfer -> `stock_transfers`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| transferNumber | String | `transfer_number` |
| date | DateTime | `date` |
| fromWarehouseId | String | `from_warehouse_id` |
| fromWarehouse | Warehouse | -> Warehouse via `from_warehouse_id` |
| toWarehouseId | String | `to_warehouse_id` |
| toWarehouse | Warehouse | -> Warehouse via `to_warehouse_id` |
| status | DocumentStatus | `status` |
| notes | String? | `notes` |
| items | StockTransferItem[] | <- StockTransferItem.transfer (list) |
| nomorMobil | String? | `nomor_mobil` |
| driverName | String? | `driver_name` |
| recipientName | String? | `recipient_name` |
| printedAt | DateTime? | `printed_at` |
| fiscalYearId | String | `fiscal_year_id` |
| fiscalYear | FiscalYear | -> FiscalYear via `fiscal_year_id` |
| createdBy | String | `created_by` |
| user | User | -> User via `created_by` |
| submittedAt | DateTime? | `submitted_at` |
| cancelledAt | DateTime? | `cancelled_at` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## StockTransferItem -> `stock_transfer_items`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| transferId | String | `transfer_id` |
| transfer | StockTransfer | -> StockTransfer via `transfer_id` |
| itemId | String | `item_id` |
| item | InventoryItem | -> InventoryItem via `item_id` |
| quantity | Decimal | `quantity` |
| description | String? | `description` |
| sourceMovementId | String? | `source_movement_id` |
| sourceMovement | StockMovement | -> StockMovement via `source_movement_id` |
| createdAt | DateTime | `created_at` |

## StockMovement -> `stock_movements`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| movementNumber | String | `movement_number` |
| date | DateTime | `date` |
| itemId | String | `item_id` |
| item | InventoryItem | -> InventoryItem via `item_id` |
| warehouseId | String? | `warehouse_id` |
| warehouse | Warehouse | -> Warehouse via `warehouse_id` |
| movementType | StockMovementType | `movement_type` |
| quantity | Decimal | `quantity` |
| unitCost | Decimal | `unit_cost` |
| totalValue | Decimal | `total_value` |
| notes | String? | `notes` |
| referenceType | String? | `reference_type` |
| referenceId | String? | `reference_id` |
| referenceNumber | String? | `reference_number` |
| offsetAccountId | String? | `offset_account_id` |
| offsetAccount | Account | -> Account via `offset_account_id` |
| journalEntryId | String? | `journal_entry_id` |
| journalEntry | JournalEntry | -> JournalEntry via `journal_entry_id` |
| fiscalYearId | String | `fiscal_year_id` |
| fiscalYear | FiscalYear | -> FiscalYear via `fiscal_year_id` |
| createdById | String | `created_by_id` |
| createdBy | User | -> User via `created_by_id` |
| isCancelled | Boolean | `is_cancelled` |
| unitCostLoaded | Decimal? | `unit_cost_loaded` |
| sourceMovementId | String? | `source_movement_id` |
| sourceMovement | StockMovement | -> StockMovement via `source_movement_id` |
| consumingMovements | StockMovement[] | <- StockMovement.sourceMovement (list) |
| deliveryOrderItem | DeliveryOrderItem | <- DeliveryOrderItem.stockMovement (1:1, field `deliveryOrderItemRef`) |
| goodsReceiptItem | GoodsReceiptItem | <- GoodsReceiptItem.stockMovement (1:1, field `goodsReceiptItemRef`) |
| productionInputBatchUsage | ProductionRunItem[] | <- ProductionRunItem.sourceMovement (list) |
| consumingSalesItems | SalesInvoiceItem[] | <- SalesInvoiceItem.sourceMovement (list) |
| consumingTransferItems | StockTransferItem[] | <- StockTransferItem.sourceMovement (list) |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## ProductionRunDraft -> `production_run_drafts`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| draftNumber | String | `draft_number` |
| date | DateTime | `date` |
| notes | String? | `notes` |
| warehouseId | String? | `warehouse_id` |
| summary | Json? | `summary` |
| payload | Json | `payload` |
| status | String | `status` |
| createdById | String | `created_by_id` |
| updatedById | String? | `updated_by_id` |
| postedRunId | String? | `posted_run_id` |
| postedRunNumber | String? | `posted_run_number` |
| postedAt | DateTime? | `posted_at` |
| postedById | String? | `posted_by_id` |
| approvalRequestId | String? | `approval_request_id` |
| lastError | String? | `last_error` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## CashBookDraft -> `cash_book_drafts`
_Draft Buku Kas (dari nota / halaman buku kas). Tidak berefek ke GL sampai diposting._

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| draftNumber | String | `draft_number` |
| status | String | `status` |
| title | String | `title` |
| entryDate | DateTime | `entry_date` |
| cashAccountId | String | `cash_account_id` |
| openingBalance | Decimal? | `opening_balance` |
| closingBalance | Decimal? | `closing_balance` |
| source | String | `source` |
| sourceRef | String? | `source_ref` |
| sourceNote | String? | `source_note` |
| createdById | String | `created_by_id` |
| createdVia | String? | `created_via` |
| postedAt | DateTime? | `posted_at` |
| postedById | String? | `posted_by_id` |
| lastError | String? | `last_error` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |
| rows | CashBookDraftRow[] | <- CashBookDraftRow.draft (list) |

## CashBookDraftRow -> `cash_book_draft_rows`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| draftId | String | `draft_id` |
| draft | CashBookDraft | -> CashBookDraft via `draft_id` |
| idx | Int | `idx` |
| rowDate | DateTime | `row_date` |
| description | String | `description` |
| amount | Decimal | `amount` |
| direction | String | `direction` |
| txnType | String | `txn_type` |
| accountId | String? | `account_id` |
| accountCode | String? | `account_code` |
| accountLabel | String? | `account_label` |
| confidence | Decimal | `confidence` |
| needsReview | Boolean | `needs_review` |
| reason | String? | `reason` |
| skip | Boolean | `skip` |
| userEdited | Boolean | `user_edited` |
| journalEntryId | String? | `journal_entry_id` |
| postedAt | DateTime? | `posted_at` |
| error | String? | `error` |

## CashBookLearnedMap -> `cash_book_learned_maps`
_Pola keterangan ternormalisasi -> akun, dipelajari dari koreksi manusia saat posting._

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| pattern | String | `pattern` |
| txnType | String | `txn_type` |
| accountId | String | `account_id` |
| hits | Int | `hits` |
| updatedBy | String | `updated_by` |
| updatedAt | DateTime | `updated_at` |

## ProductionRun -> `production_runs`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| runNumber | String | `run_number` |
| date | DateTime | `date` |
| notes | String? | `notes` |
| referenceType | String? | `reference_type` |
| referenceId | String? | `reference_id` |
| referenceNumber | String? | `reference_number` |
| tollOrderId | String? | `toll_order_id` |
| yieldPct | Decimal? | `yield_pct` |
| totalMaterialCost | Decimal? | `total_material_cost` |
| totalCostInputSide | Decimal? | `total_cost_input_side` |
| totalCostOutputSide | Decimal? | `total_cost_output_side` |
| totalByproductValue | Decimal? | `total_byproduct_value` |
| computedHpp | Decimal? | `computed_hpp` |
| hppManualOverride | Boolean | `hpp_manual_override` |
| machineId | String? | `machine_id` |
| machine | Machine | -> Machine via `machine_id` |
| shift | String? | `shift` |
| shiftLead | String? | `shift_lead` |
| manpowerCount | Int? | `manpower_count` |
| startAt | DateTime? | `start_at` |
| endAt | DateTime? | `end_at` |
| goodQty | Decimal? | `good_qty` |
| rejectQty | Decimal? | `reject_qty` |
| downtimeMin | Decimal? | `downtime_min` |
| availabilityPct | Decimal? | `availability_pct` |
| performancePct | Decimal? | `performance_pct` |
| qualityPct | Decimal? | `quality_pct` |
| oeePct | Decimal? | `oee_pct` |
| plannedStart | DateTime? | `planned_start` |
| plannedEnd | DateTime? | `planned_end` |
| breakStart | DateTime? | `break_start` |
| breakEnd | DateTime? | `break_end` |
| runMachines | ProductionRunMachine[] | <- ProductionRunMachine.productionRun (list) |
| downtimes | ProductionDowntime[] | <- ProductionDowntime.productionRun (list) |
| items | ProductionRunItem[] | <- ProductionRunItem.productionRun (list) |
| runCosts | ProductionRunCost[] | <- ProductionRunCost.productionRun (list) |
| runProcesses | ProductionRunProcess[] | <- ProductionRunProcess.productionRun (list) |
| isCancelled | Boolean | `is_cancelled` |
| fiscalYearId | String | `fiscal_year_id` |
| fiscalYear | FiscalYear | -> FiscalYear via `fiscal_year_id` |
| createdById | String | `created_by_id` |
| createdBy | User | -> User via `created_by_id` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## Machine -> `machines`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| code | String | `code` |
| name | String | `name` |
| idealRatePerHour | Decimal? | `ideal_rate_per_hour` |
| unit | String | `unit` |
| isActive | Boolean | `is_active` |
| notes | String? | `notes` |
| productionRuns | ProductionRun[] | <- ProductionRun.machine (list) |
| runMachines | ProductionRunMachine[] | <- ProductionRunMachine.machine (list) |
| downtimes | ProductionDowntime[] | <- ProductionDowntime.machine (list) |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## ProductionDowntime -> `production_downtimes`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| productionRunId | String | `production_run_id` |
| productionRun | ProductionRun | -> ProductionRun via `production_run_id` |
| machineId | String? | `machine_id` |
| machine | Machine | -> Machine via `machine_id` |
| category | String | `category` |
| reason | String? | `reason` |
| durationMin | Decimal | `duration_min` |
| createdAt | DateTime | `created_at` |

## ProductionRunMachine -> `production_run_machines`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| productionRunId | String | `production_run_id` |
| productionRun | ProductionRun | -> ProductionRun via `production_run_id` |
| machineId | String | `machine_id` |
| machine | Machine | -> Machine via `machine_id` |
| startAt | DateTime? | `start_at` |
| endAt | DateTime? | `end_at` |
| runMin | Decimal? | `run_min` |
| downtimeMin | Decimal? | `downtime_min` |
| allocatedQty | Decimal? | `allocated_qty` |
| availabilityPct | Decimal? | `availability_pct` |
| performancePct | Decimal? | `performance_pct` |
| qualityPct | Decimal? | `quality_pct` |
| oeePct | Decimal? | `oee_pct` |
| createdAt | DateTime | `created_at` |

## ProductionRunItem -> `production_run_items`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| productionRunId | String | `production_run_id` |
| productionRun | ProductionRun | -> ProductionRun via `production_run_id` |
| itemId | String | `item_id` |
| item | InventoryItem | -> InventoryItem via `item_id` |
| warehouseId | String? | `warehouse_id` |
| warehouse | Warehouse | -> Warehouse via `warehouse_id` |
| lineType | String | `line_type` |
| quantity | Decimal | `quantity` |
| unitPrice | Decimal? | `unit_price` |
| yieldPct | Decimal? | `yield_pct` |
| isByProduct | Boolean | `is_by_product` |
| sourceMovementId | String? | `source_movement_id` |
| sourceMovement | StockMovement | -> StockMovement via `source_movement_id` |
| batchUnitCost | Decimal? | `batch_unit_cost` |
| nrvUnitValue | Decimal? | `nrv_unit_value` |
| createdAt | DateTime | `created_at` |

## ProductionCostStandard -> `production_cost_standards`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| name | String | `name` |
| description | String? | `description` |
| costSide | String | `cost_side` |
| basis | String | `basis` |
| defaultRate | Decimal | `default_rate` |
| costAccountId | String? | `cost_account_id` |
| costAccount | Account | -> Account via `cost_account_id` |
| itemId | String? | `item_id` |
| item | InventoryItem | -> InventoryItem via `item_id` |
| warehouseId | String? | `warehouse_id` |
| warehouse | Warehouse | -> Warehouse via `warehouse_id` |
| isActive | Boolean | `is_active` |
| runCosts | ProductionRunCost[] | <- ProductionRunCost.standard (list) |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## ProductionRunCost -> `production_run_costs`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| productionRunId | String | `production_run_id` |
| productionRun | ProductionRun | -> ProductionRun via `production_run_id` |
| standardId | String? | `standard_id` |
| standard | ProductionCostStandard | -> ProductionCostStandard via `standard_id` |
| description | String | `description` |
| costSide | String | `cost_side` |
| basis | String | `basis` |
| rate | Decimal | `rate` |
| qtyBasis | Decimal | `qty_basis` |
| totalAmount | Decimal | `total_amount` |
| costAccountId | String? | `cost_account_id` |
| costAccount | Account | -> Account via `cost_account_id` |
| createdAt | DateTime | `created_at` |

## ProductionProcess -> `production_processes`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| name | String | `name` |
| description | String? | `description` |
| sortOrder | Int | `sort_order` |
| isActive | Boolean | `is_active` |
| runProcesses | ProductionRunProcess[] | <- ProductionRunProcess.process (list) |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## ProductionRunProcess -> `production_run_processes`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| productionRunId | String | `production_run_id` |
| productionRun | ProductionRun | -> ProductionRun via `production_run_id` |
| processId | String | `process_id` |
| process | ProductionProcess | -> ProductionProcess via `process_id` |
| createdAt | DateTime | `created_at` |

## StockOpname -> `stock_opnames`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| opnameNumber | String | `opname_number` |
| date | DateTime | `date` |
| status | StockOpnameStatus | `status` |
| notes | String? | `notes` |
| warehouseId | String? | `warehouse_id` |
| warehouse | Warehouse | -> Warehouse via `warehouse_id` |
| items | StockOpnameItem[] | <- StockOpnameItem.stockOpname (list) |
| fiscalYearId | String | `fiscal_year_id` |
| fiscalYear | FiscalYear | -> FiscalYear via `fiscal_year_id` |
| createdById | String | `created_by_id` |
| createdBy | User | -> User via `created_by_id` |
| submittedAt | DateTime? | `submitted_at` |
| cancelledAt | DateTime? | `cancelled_at` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## StockOpnameItem -> `stock_opname_items`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| stockOpnameId | String | `stock_opname_id` |
| stockOpname | StockOpname | -> StockOpname via `stock_opname_id` |
| itemId | String | `item_id` |
| item | InventoryItem | -> InventoryItem via `item_id` |
| warehouseId | String? | `warehouse_id` |
| warehouse | Warehouse | -> Warehouse via `warehouse_id` |
| systemStock | Decimal | `system_stock` |
| actualStock | Decimal | `actual_stock` |
| difference | Decimal | `difference` |
| unitCost | Decimal | `unit_cost` |
| totalValue | Decimal | `total_value` |
| movementId | String? | `movement_id` |
| notes | String? | `notes` |
| createdAt | DateTime | `created_at` |

## Notification -> `notifications`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| userId | String | `user_id` |
| user | User | -> User via `user_id` |
| type | String | `type` |
| title | String | `title` |
| message | String | `message` |
| isRead | Boolean | `is_read` |
| metadata | Json? | `metadata` |
| createdAt | DateTime | `created_at` |

## RecurringTemplate -> `recurring_templates`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| name | String | `name` |
| templateType | String | `template_type` |
| frequency | String | `frequency` |
| dayOfMonth | Int? | `day_of_month` |
| nextRunDate | DateTime | `next_run_date` |
| lastRunDate | DateTime? | `last_run_date` |
| isActive | Boolean | `is_active` |
| templateData | Json | `template_data` |
| createdBy | String | `created_by` |
| user | User | -> User via `created_by` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## BankReconciliation -> `bank_reconciliations`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| accountId | String | `account_id` |
| account | Account | -> Account via `account_id` |
| statementDate | DateTime | `statement_date` |
| statementBalance | Decimal | `statement_balance` |
| bookBalance | Decimal | `book_balance` |
| status | String | `status` |
| notes | String? | `notes` |
| items | BankReconciliationItem[] | <- BankReconciliationItem.reconciliation (list) |
| createdBy | String | `created_by` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## BankReconciliationItem -> `bank_reconciliation_items`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| reconciliationId | String | `reconciliation_id` |
| reconciliation | BankReconciliation | -> BankReconciliation via `reconciliation_id` |
| ledgerEntryId | String? | `ledger_entry_id` |
| isMatched | Boolean | `is_matched` |
| statementAmount | Decimal? | `statement_amount` |
| statementDesc | String? | `statement_desc` |
| statementDate | DateTime? | `statement_date` |
| createdAt | DateTime | `created_at` |

## TaxConfig -> `tax_configs`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| name | String | `name` |
| rate | Decimal | `rate` |
| type | String | `type` |
| accountId | String? | `account_id` |
| isActive | Boolean | `is_active` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## VendorDepositApplication -> `vendor_deposit_applications`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| depositPaymentId | String | `deposit_payment_id` |
| depositPayment | Payment | -> Payment via `deposit_payment_id` |
| purchaseInvoiceId | String | `purchase_invoice_id` |
| purchaseInvoice | PurchaseInvoice | -> PurchaseInvoice via `purchase_invoice_id` |
| appliedAmount | Decimal | `applied_amount` |
| journalEntryId | String? | `journal_entry_id` |
| journalEntry | JournalEntry | -> JournalEntry via `journal_entry_id` |
| isCancelled | Boolean | `is_cancelled` |
| appliedAt | DateTime | `applied_at` |
| cancelledAt | DateTime? | `cancelled_at` |
| createdBy | String | `created_by` |
| user | User | -> User via `created_by` |

## CustomerDepositApplication -> `customer_deposit_applications`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| depositPaymentId | String | `deposit_payment_id` |
| depositPayment | Payment | -> Payment via `deposit_payment_id` |
| salesInvoiceId | String | `sales_invoice_id` |
| salesInvoice | SalesInvoice | -> SalesInvoice via `sales_invoice_id` |
| appliedAmount | Decimal | `applied_amount` |
| journalEntryId | String? | `journal_entry_id` |
| journalEntry | JournalEntry | -> JournalEntry via `journal_entry_id` |
| isCancelled | Boolean | `is_cancelled` |
| appliedAt | DateTime | `applied_at` |
| cancelledAt | DateTime? | `cancelled_at` |
| createdBy | String | `created_by` |
| user | User | -> User via `created_by` |

## ServiceItem -> `service_items`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| code | String | `code` |
| name | String | `name` |
| unit | String | `unit` |
| defaultRate | Decimal? | `default_rate` |
| pricingBasis | String? | `pricing_basis` |
| minimumCharge | Decimal? | `minimum_charge` |
| accountId | String | `account_id` |
| account | Account | -> Account via `account_id` |
| description | String? | `description` |
| isActive | Boolean | `is_active` |
| isDummy | Boolean | `is_dummy` |
| salesInvoiceItems | SalesInvoiceItem[] | <- SalesInvoiceItem.serviceItem (list) |
| quotationItems | QuotationItem[] | <- QuotationItem.serviceItem (list) |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## TransactionAttachment -> `transaction_attachments`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| referenceType | String | `reference_type` |
| referenceId | String | `reference_id` |
| fileName | String | `file_name` |
| storedName | String | `stored_name` |
| mimeType | String | `mime_type` |
| fileSize | Int | `file_size` |
| filePath | String | `file_path` |
| uploadedBy | String | `uploaded_by` |
| user | User | -> User via `uploaded_by` |
| createdAt | DateTime | `created_at` |

## FixedAsset -> `fixed_assets`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| assetNumber | String | `asset_number` |
| gaAssetId | String? | `ga_asset_id` |
| gaAsset | GaAsset | -> GaAsset via `ga_asset_id` |
| name | String | `name` |
| category | String | `category` |
| description | String? | `description` |
| acquisitionDate | DateTime | `acquisition_date` |
| acquisitionCost | Decimal | `acquisition_cost` |
| usefulLifeMonths | Int | `useful_life_months` |
| salvageValue | Decimal | `salvage_value` |
| depreciationMethod | String | `depreciation_method` |
| accumulatedDepreciation | Decimal | `accumulated_depreciation` |
| bookValue | Decimal | `book_value` |
| status | String | `status` |
| disposalDate | DateTime? | `disposal_date` |
| disposalAmount | Decimal? | `disposal_amount` |
| assetAccountId | String | `asset_account_id` |
| assetAccount | Account | -> Account via `asset_account_id` |
| depreciationAccountId | String | `depreciation_account_id` |
| depreciationAccount | Account | -> Account via `depreciation_account_id` |
| accumulatedDepAccountId | String | `accumulated_dep_account_id` |
| accumulatedDepAccount | Account | -> Account via `accumulated_dep_account_id` |
| fiscalYearId | String | `fiscal_year_id` |
| fiscalYear | FiscalYear | -> FiscalYear via `fiscal_year_id` |
| createdBy | String | `created_by` |
| user | User | -> User via `created_by` |
| depreciationEntries | DepreciationEntry[] | <- DepreciationEntry.fixedAsset (list) |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## DepreciationEntry -> `depreciation_entries`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| fixedAssetId | String | `fixed_asset_id` |
| fixedAsset | FixedAsset | -> FixedAsset via `fixed_asset_id` |
| periodDate | DateTime | `period_date` |
| amount | Decimal | `amount` |
| journalEntryId | String? | `journal_entry_id` |
| journalEntry | JournalEntry | -> JournalEntry via `journal_entry_id` |
| isPosted | Boolean | `is_posted` |
| createdAt | DateTime | `created_at` |

## ApprovalRule -> `approval_rules`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| documentType | String | `document_type` |
| minAmount | Decimal | `min_amount` |
| requiredRole | String | `required_role` |
| isActive | Boolean | `is_active` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## ApprovalRequest -> `approval_requests`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| documentType | String | `document_type` |
| documentId | String? | `document_id` |
| documentNumber | String? | `document_number` |
| amount | Decimal? | `amount` |
| payload | Json? | `payload` |
| status | String | `status` |
| requestedBy | String | `requested_by` |
| requester | User | -> User via `requested_by` |
| approvedBy | String? | `approved_by` |
| approver | User | -> User via `approved_by` |
| rejectedBy | String? | `rejected_by` |
| rejecter | User | -> User via `rejected_by` |
| notes | String? | `notes` |
| decidedAt | DateTime? | `decided_at` |
| createdAt | DateTime | `created_at` |

## CogsBackfillQueue -> `cogs_backfill_queue`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| salesInvoiceId | String | `sales_invoice_id` |
| salesInvoice | SalesInvoice | -> SalesInvoice via `sales_invoice_id` |
| salesInvoiceItemId | String | `sales_invoice_item_id` |
| salesInvoiceItem | SalesInvoiceItem | -> SalesInvoiceItem via `sales_invoice_item_id` |
| inventoryItemId | String | `inventory_item_id` |
| inventoryItem | InventoryItem | -> InventoryItem via `inventory_item_id` |
| qtyPending | Decimal | `qty_pending` |
| qtyOriginal | Decimal | `qty_original` |
| costAtSale | Decimal | `cost_at_sale` |
| status | String | `status` |
| fiscalYearId | String | `fiscal_year_id` |
| fiscalYear | FiscalYear | -> FiscalYear via `fiscal_year_id` |
| settlements | CogsBackfillSettlement[] | <- CogsBackfillSettlement.queue (list) |
| createdAt | DateTime | `created_at` |
| settledAt | DateTime? | `settled_at` |

## CogsBackfillSettlement -> `cogs_backfill_settlements`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| queueId | String | `queue_id` |
| queue | CogsBackfillQueue | -> CogsBackfillQueue via `queue_id` |
| qtySettled | Decimal | `qty_settled` |
| costAtSettle | Decimal | `cost_at_settle` |
| differential | Decimal | `differential` |
| journalEntryId | String? | `journal_entry_id` |
| journalEntry | JournalEntry | -> JournalEntry via `journal_entry_id` |
| triggerSource | String | `trigger_source` |
| triggerRefId | String? | `trigger_ref_id` |
| triggerRefNo | String? | `trigger_ref_no` |
| createdAt | DateTime | `created_at` |

## PurchaseOrder -> `purchase_orders`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| poNumber | String | `po_number` |
| date | DateTime | `date` |
| partyId | String? | `party_id` |
| supplier | Party | -> Party via `party_id` |
| status | DocumentStatus | `status` |
| notes | String? | `notes` |
| terms | String? | `terms` |
| expectedDeliveryDate | DateTime? | `expected_delivery_date` |
| totalEstimated | Decimal | `total_estimated` |
| subtotal | Decimal | `subtotal` |
| discountTotal | Decimal | `discount_total` |
| taxTotal | Decimal | `tax_total` |
| otherCosts | Decimal | `other_costs` |
| otherCostsLabel | String? | `other_costs_label` |
| grandTotal | Decimal | `grand_total` |
| paymentTermDays | Int? | `payment_term_days` |
| warehouseId | String? | `warehouse_id` |
| warehouse | Warehouse | -> Warehouse via `warehouse_id` |
| supplierQuoteRef | String? | `supplier_quote_ref` |
| approvalStatus | String? | `approval_status` |
| approvalNote | String? | `approval_note` |
| items | PurchaseOrderItem[] | <- PurchaseOrderItem.purchaseOrder (list) |
| goodsReceipts | GoodsReceipt[] | <- GoodsReceipt.purchaseOrder (list) |
| purchaseInvoices | PurchaseInvoice[] | <- PurchaseInvoice.purchaseOrder (list) |
| fiscalYearId | String | `fiscal_year_id` |
| fiscalYear | FiscalYear | -> FiscalYear via `fiscal_year_id` |
| createdBy | String | `created_by` |
| user | User | -> User via `created_by` |
| submittedAt | DateTime? | `submitted_at` |
| cancelledAt | DateTime? | `cancelled_at` |
| closedAt | DateTime? | `closed_at` |
| closeReason | String? | `close_reason` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## PurchaseOrderItem -> `purchase_order_items`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| purchaseOrderId | String | `purchase_order_id` |
| purchaseOrder | PurchaseOrder | -> PurchaseOrder via `purchase_order_id` |
| inventoryItemId | String? | `inventory_item_id` |
| inventoryItem | InventoryItem | -> InventoryItem via `inventory_item_id` |
| itemName | String | `item_name` |
| quantity | Decimal | `quantity` |
| unit | String | `unit` |
| estimatedRate | Decimal | `estimated_rate` |
| estimatedAmount | Decimal | `estimated_amount` |
| discount | Decimal | `discount` |
| taxPct | Decimal | `tax_pct` |
| taxAmount | Decimal | `tax_amount` |
| qtyReceived | Decimal | `qty_received` |
| description | String? | `description` |
| goodsReceiptItems | GoodsReceiptItem[] | <- GoodsReceiptItem.purchaseOrderItem (list) |

## GoodsReceipt -> `goods_receipts`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| sttNumber | String | `stt_number` |
| date | DateTime | `date` |
| partyId | String | `party_id` |
| supplier | Party | -> Party via `party_id` |
| isCustomerOwned | Boolean | `is_customer_owned` |
| tollOrderId | String? | `toll_order_id` |
| purchaseOrderId | String? | `purchase_order_id` |
| purchaseOrder | PurchaseOrder | -> PurchaseOrder via `purchase_order_id` |
| status | DocumentStatus | `status` |
| notes | String? | `notes` |
| nomorMobil | String? | `nomor_mobil` |
| driverName | String? | `driver_name` |
| recipientName | String? | `recipient_name` |
| invoiceId | String? | `invoice_id` |
| invoice | PurchaseInvoice | -> PurchaseInvoice via `invoice_id` |
| invoicedAt | DateTime? | `invoiced_at` |
| isLegacyBackfill | Boolean | `is_legacy_backfill` |
| items | GoodsReceiptItem[] | <- GoodsReceiptItem.goodsReceipt (list) |
| fiscalYearId | String | `fiscal_year_id` |
| fiscalYear | FiscalYear | -> FiscalYear via `fiscal_year_id` |
| createdBy | String | `created_by` |
| user | User | -> User via `created_by` |
| submittedAt | DateTime? | `submitted_at` |
| cancelledAt | DateTime? | `cancelled_at` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## GoodsReceiptItem -> `goods_receipt_items`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| goodsReceiptId | String | `goods_receipt_id` |
| goodsReceipt | GoodsReceipt | -> GoodsReceipt via `goods_receipt_id` |
| inventoryItemId | String | `inventory_item_id` |
| inventoryItem | InventoryItem | -> InventoryItem via `inventory_item_id` |
| warehouseId | String | `warehouse_id` |
| warehouse | Warehouse | -> Warehouse via `warehouse_id` |
| itemName | String | `item_name` |
| quantity | Decimal | `quantity` |
| unit | String | `unit` |
| lockedUnitCost | Decimal | `locked_unit_cost` |
| description | String? | `description` |
| purchaseOrderItemId | String? | `purchase_order_item_id` |
| purchaseOrderItem | PurchaseOrderItem | -> PurchaseOrderItem via `purchase_order_item_id` |
| stockMovementId | String? | `stock_movement_id` |
| stockMovement | StockMovement | -> StockMovement via `stock_movement_id` |
| invoiceItem | PurchaseInvoiceItem | <- PurchaseInvoiceItem.goodsReceiptItem (1:1, field `invoiceItemRef`) |

## SalesReturn -> `sales_returns`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| returnNumber | String | `return_number` |
| date | DateTime | `date` |
| partyId | String | `party_id` |
| customer | Party | -> Party via `party_id` |
| salesInvoiceId | String | `sales_invoice_id` |
| salesInvoice | SalesInvoice | -> SalesInvoice via `sales_invoice_id` |
| status | String | `status` |
| subtotal | Decimal | `subtotal` |
| taxAmount | Decimal | `tax_amount` |
| totalReturn | Decimal | `total_return` |
| arReducedAmount | Decimal | `ar_reduced_amount` |
| creditCreatedAmount | Decimal | `credit_created_amount` |
| customerDepositId | String? | `customer_deposit_id` |
| reason | String? | `reason` |
| notes | String? | `notes` |
| fiscalYearId | String | `fiscal_year_id` |
| createdBy | String | `created_by` |
| items | SalesReturnItem[] | <- SalesReturnItem.salesReturn (list) |
| submittedAt | DateTime? | `submitted_at` |
| cancelledAt | DateTime? | `cancelled_at` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## SalesReturnItem -> `sales_return_items`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| salesReturnId | String | `sales_return_id` |
| salesReturn | SalesReturn | -> SalesReturn via `sales_return_id` |
| salesInvoiceItemId | String | `sales_invoice_item_id` |
| salesInvoiceItem | SalesInvoiceItem | -> SalesInvoiceItem via `sales_invoice_item_id` |
| inventoryItemId | String? | `inventory_item_id` |
| warehouseId | String? | `warehouse_id` |
| accountId | String | `account_id` |
| itemName | String | `item_name` |
| quantity | Decimal | `quantity` |
| unit | String | `unit` |
| rate | Decimal | `rate` |
| discount | Decimal | `discount` |
| amount | Decimal | `amount` |
| taxPct | Decimal | `tax_pct` |
| restock | Boolean | `restock` |
| unitCostLoaded | Decimal? | `unit_cost_loaded` |
| stockMovementId | String? | `stock_movement_id` |

## ProjectGuarantee -> `project_guarantees`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| guaranteeNumber | String | `guarantee_number` |
| date | DateTime | `date` |
| partyId | String | `party_id` |
| party | Party | -> Party via `party_id` |
| guaranteeType | String | `guarantee_type` |
| projectName | String | `project_name` |
| referenceNo | String? | `reference_no` |
| amount | Decimal | `amount` |
| accountId | String | `account_id` |
| expectedReturnDate | DateTime? | `expected_return_date` |
| notes | String? | `notes` |
| status | String | `status` |
| journalEntryId | String? | `journal_entry_id` |
| fiscalYearId | String | `fiscal_year_id` |
| createdBy | String | `created_by` |
| settlements | ProjectGuaranteeSettlement[] | <- ProjectGuaranteeSettlement.guarantee (list) |
| submittedAt | DateTime? | `submitted_at` |
| cancelledAt | DateTime? | `cancelled_at` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## ProjectGuaranteeSettlement -> `project_guarantee_settlements`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| guaranteeId | String | `guarantee_id` |
| guarantee | ProjectGuarantee | -> ProjectGuarantee via `guarantee_id` |
| date | DateTime | `date` |
| returnAmount | Decimal | `return_amount` |
| forfeitAmount | Decimal | `forfeit_amount` |
| returnAccountId | String? | `return_account_id` |
| forfeitAccountId | String? | `forfeit_account_id` |
| journalEntryId | String? | `journal_entry_id` |
| notes | String? | `notes` |
| isCancelled | Boolean | `is_cancelled` |
| cancelledAt | DateTime? | `cancelled_at` |
| createdBy | String | `created_by` |
| createdAt | DateTime | `created_at` |

## GaBranch -> `ga_branches`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| code | String | `code` |
| name | String | `name` |
| address | String? | `address` |
| isActive | Boolean | `is_active` |
| createdAt | DateTime | `created_at` |
| costCenters | GaCostCenter[] | <- GaCostCenter.branch (list) |
| assets | GaAsset[] | <- GaAsset.branch (list) |
| locations | GaLocation[] | <- GaLocation.branch (list) |

## GaCostCenter -> `ga_cost_centers`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| code | String | `code` |
| name | String | `name` |
| branchId | String | `branch_id` |
| isActive | Boolean | `is_active` |
| branch | GaBranch | -> GaBranch via `branch_id` |
| assets | GaAsset[] | <- GaAsset.costCenter (list) |
| paymentRequests | PaymentRequest[] | <- PaymentRequest.costCenter (list) |
| paymentRequestLines | PaymentRequestLine[] | <- PaymentRequestLine.costCenter (list) |

## GaAssetCategory -> `ga_asset_categories`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| code | String | `code` |
| name | String | `name` |
| customFieldsSchema | Json? | `custom_fields_schema` |
| maintenanceChecklistSchema | Json? | `maintenance_checklist_schema` |
| isActive | Boolean | `is_active` |
| assets | GaAsset[] | <- GaAsset.category (list) |

## GaAsset -> `ga_assets`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| code | String | `code` |
| name | String | `name` |
| categoryId | String | `category_id` |
| branchId | String | `branch_id` |
| costCenterId | String? | `cost_center_id` |
| picUserId | String? | `pic_user_id` |
| serialNumber | String? | `serial_number` |
| acquisitionDate | DateTime? | `acquisition_date` |
| acquisitionCost | Decimal? | `acquisition_cost` |
| status | GaAssetStatus | `status` |
| condition | GaAssetCondition | `condition` |
| location | String? | `location` |
| notes | String? | `notes` |
| customFields | Json? | `custom_fields` |
| maintenanceChecklistOverride | Json? | `maintenance_checklist_override` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |
| category | GaAssetCategory | -> GaAssetCategory via `category_id` |
| branch | GaBranch | -> GaBranch via `branch_id` |
| costCenter | GaCostCenter | -> GaCostCenter via `cost_center_id` |
| photos | GaAssetPhoto[] | <- GaAssetPhoto.asset (list) |
| qr | GaAssetQr | <- GaAssetQr.asset (1:1, field `qrRef`) |
| documents | GaDocument[] | <- GaDocument.asset (list) |
| maintenanceSchedules | GaMaintenanceSchedule[] | <- GaMaintenanceSchedule.asset (list) |
| workOrders | GaWorkOrder[] | <- GaWorkOrder.asset (list) |
| vehicleData | GaVehicleData | <- GaVehicleData.asset (1:1, field `vehicleDataRef`) |
| vehicleServiceLogs | GaVehicleServiceLog[] | <- GaVehicleServiceLog.asset (list) |
| vehicleServiceRequests | GaVehicleServiceRequest[] | <- GaVehicleServiceRequest.asset (list) |
| buildingData | GaBuildingData | <- GaBuildingData.asset (1:1, field `buildingDataRef`) |
| buildingRentals | GaBuildingRental[] | <- GaBuildingRental.asset (list) |
| equipmentData | GaEquipmentData | <- GaEquipmentData.asset (1:1, field `equipmentDataRef`) |
| fixedAsset | FixedAsset | <- FixedAsset.gaAsset (1:1, field `fixedAssetRef`) |
| calibrationLogs | GaCalibrationLog[] | <- GaCalibrationLog.asset (list) |
| materialRequests | GaMaterialRequest[] | <- GaMaterialRequest.asset (list) |
| vehicleTaxes | GaVehicleTax[] | <- GaVehicleTax.asset (list) |
| vehicleTaxRequests | GaVehicleTaxRequest[] | <- GaVehicleTaxRequest.asset (list) |
| vehicleInsurances | GaVehicleInsurance[] | <- GaVehicleInsurance.asset (list) |
| vehicleFuelLogs | GaVehicleFuelLog[] | <- GaVehicleFuelLog.asset (list) |
| vehicleInspections | GaVehicleInspection[] | <- GaVehicleInspection.asset (list) |
| vehicleIncidents | GaVehicleIncident[] | <- GaVehicleIncident.asset (list) |
| vehicleTires | GaVehicleTire[] | <- GaVehicleTire.asset (list) |
| vehicleOilLogs | GaVehicleOilLog[] | <- GaVehicleOilLog.asset (list) |

## GaAssetPhoto -> `ga_asset_photos`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| assetId | String | `asset_id` |
| filePath | String | `file_path` |
| caption | String? | `caption` |
| orderIdx | Int | `order_idx` |
| createdAt | DateTime | `created_at` |
| asset | GaAsset | -> GaAsset via `asset_id` |

## GaAssetQr -> `ga_asset_qrs`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| assetId | String | `asset_id` |
| qrCode | String | `qr_code` |
| createdAt | DateTime | `created_at` |
| asset | GaAsset | -> GaAsset via `asset_id` |

## GaDocumentType -> `ga_document_types`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| code | String | `code` |
| name | String | `name` |
| category | String? | `category` |
| customFieldsSchema | Json? | `custom_fields_schema` |
| isActive | Boolean | `is_active` |
| documents | GaDocument[] | <- GaDocument.type (list) |

## GaDocument -> `ga_documents`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| code | String | `code` |
| title | String | `title` |
| typeId | String | `type_id` |
| assetId | String? | `asset_id` |
| issuedBy | String? | `issued_by` |
| issuedNumber | String? | `issued_number` |
| effectiveDate | DateTime? | `effective_date` |
| expiryDate | DateTime? | `expiry_date` |
| status | GaDocumentStatus | `status` |
| customFields | Json? | `custom_fields` |
| createdBy | String? | `created_by` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |
| type | GaDocumentType | -> GaDocumentType via `type_id` |
| asset | GaAsset | -> GaAsset via `asset_id` |
| versions | GaDocumentVersion[] | <- GaDocumentVersion.document (list) |
| buildingRentals | GaBuildingRental[] | <- GaBuildingRental.document (list) |
| calibrationLog | GaCalibrationLog | <- GaCalibrationLog.document (1:1, field `calibrationLogRef`) |
| vehicleTaxes | GaVehicleTax[] | <- GaVehicleTax.document (list) |
| vehicleInsurances | GaVehicleInsurance[] | <- GaVehicleInsurance.document (list) |

## GaDocumentVersion -> `ga_document_versions`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| documentId | String | `document_id` |
| versionNo | Int | `version_no` |
| filePath | String | `file_path` |
| fileSize | Int | `file_size` |
| uploadedBy | String | `uploaded_by` |
| notes | String? | `notes` |
| createdAt | DateTime | `created_at` |
| document | GaDocument | -> GaDocument via `document_id` |

## GaInventoryCategory -> `ga_inventory_categories`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| code | String | `code` |
| name | String | `name` |
| type | GaInventoryType | `type` |
| items | GaInventoryItem[] | <- GaInventoryItem.category (list) |

## GaLocation -> `ga_locations`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| code | String | `code` |
| name | String | `name` |
| branchId | String | `branch_id` |
| branch | GaBranch | -> GaBranch via `branch_id` |
| stocks | GaStockByLocation[] | <- GaStockByLocation.location (list) |
| movsFrom | GaInventoryMovement[] | <- GaInventoryMovement.fromLocation (list) |
| movsTo | GaInventoryMovement[] | <- GaInventoryMovement.toLocation (list) |
| workOrderParts | GaWorkOrderPart[] | <- GaWorkOrderPart.fromLocation (list) |
| materialRequestItems | GaMaterialRequestItem[] | <- GaMaterialRequestItem.receivedIntoLocation (list) |

## GaInventoryItem -> `ga_inventory_items`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| sku | String | `sku` |
| name | String | `name` |
| categoryId | String | `category_id` |
| uom | String | `uom` |
| minStock | Decimal | `min_stock` |
| imagePath | String? | `image_path` |
| isActive | Boolean | `is_active` |
| createdAt | DateTime | `created_at` |
| category | GaInventoryCategory | -> GaInventoryCategory via `category_id` |
| stocks | GaStockByLocation[] | <- GaStockByLocation.item (list) |
| movements | GaInventoryMovement[] | <- GaInventoryMovement.item (list) |
| workOrderParts | GaWorkOrderPart[] | <- GaWorkOrderPart.inventoryItem (list) |
| materialRequestItems | GaMaterialRequestItem[] | <- GaMaterialRequestItem.inventoryItem (list) |

## GaStockByLocation -> `ga_stock_by_location`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| itemId | String | `item_id` |
| locationId | String | `location_id` |
| quantity | Decimal | `quantity` |
| updatedAt | DateTime | `updated_at` |
| item | GaInventoryItem | -> GaInventoryItem via `item_id` |
| location | GaLocation | -> GaLocation via `location_id` |

## GaInventoryMovement -> `ga_inventory_movements`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| itemId | String | `item_id` |
| type | GaMovementType | `type` |
| quantity | Decimal | `quantity` |
| fromLocationId | String? | `from_location_id` |
| toLocationId | String? | `to_location_id` |
| referenceNo | String? | `reference_no` |
| notes | String? | `notes` |
| createdBy | String | `created_by` |
| createdAt | DateTime | `created_at` |
| item | GaInventoryItem | -> GaInventoryItem via `item_id` |
| fromLocation | GaLocation | -> GaLocation via `from_location_id` |
| toLocation | GaLocation | -> GaLocation via `to_location_id` |
| workOrderPart | GaWorkOrderPart | <- GaWorkOrderPart.movement (1:1, field `workOrderPartRef`) |

## GaMaintenanceSchedule -> `ga_maintenance_schedules`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| assetId | String | `asset_id` |
| intervalDays | Int? | `interval_days` |
| intervalHours | Int? | `interval_hours` |
| intervalKm | Int? | `interval_km` |
| lastDoneAtKm | Int? | `last_done_at_km` |
| nextDueAtKm | Int? | `next_due_at_km` |
| nextDueAt | DateTime | `next_due_at` |
| lastDoneAt | DateTime? | `last_done_at` |
| isActive | Boolean | `is_active` |
| notes | String? | `notes` |
| createdBy | String | `created_by` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |
| asset | GaAsset | -> GaAsset via `asset_id` |
| workOrders | GaWorkOrder[] | <- GaWorkOrder.schedule (list) |

## GaWorkOrder -> `ga_work_orders`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| code | String | `code` |
| assetId | String | `asset_id` |
| type | GaWorkOrderType | `type` |
| status | GaWorkOrderStatus | `status` |
| priority | GaWorkOrderPriority | `priority` |
| title | String | `title` |
| description | String? | `description` |
| reportedById | String? | `reported_by_id` |
| reportedAt | DateTime | `reported_at` |
| assignedToId | String? | `assigned_to_id` |
| assignedAt | DateTime? | `assigned_at` |
| assignedById | String? | `assigned_by_id` |
| startedAt | DateTime? | `started_at` |
| completedAt | DateTime? | `completed_at` |
| completedById | String? | `completed_by_id` |
| resolution | String? | `resolution` |
| wasLate | Boolean | `was_late` |
| closedAt | DateTime? | `closed_at` |
| closedById | String? | `closed_by_id` |
| cancelledAt | DateTime? | `cancelled_at` |
| cancelledById | String? | `cancelled_by_id` |
| cancelReason | String? | `cancel_reason` |
| scheduleId | String? | `schedule_id` |
| dueAt | DateTime? | `due_at` |
| laborHours | Decimal? | `labor_hours` |
| laborCost | Decimal? | `labor_cost` |
| externalVendor | String? | `external_vendor` |
| externalCost | Decimal? | `external_cost` |
| component | String? | `component` |
| checklist | Json? | `checklist` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |
| asset | GaAsset | -> GaAsset via `asset_id` |
| schedule | GaMaintenanceSchedule | -> GaMaintenanceSchedule via `schedule_id` |
| photos | GaWorkOrderPhoto[] | <- GaWorkOrderPhoto.workOrder (list) |
| parts | GaWorkOrderPart[] | <- GaWorkOrderPart.workOrder (list) |
| materialRequests | GaMaterialRequest[] | <- GaMaterialRequest.workOrder (list) |
| vehicleServiceLog | GaVehicleServiceLog | <- GaVehicleServiceLog.workOrder (1:1, field `vehicleServiceLogRef`) |
| vehicleServiceReq | GaVehicleServiceRequest | <- GaVehicleServiceRequest.workOrder (1:1, field `vehicleServiceReqRef`) |
| calibrationLog | GaCalibrationLog | <- GaCalibrationLog.workOrder (1:1, field `calibrationLogRef`) |

## GaWorkOrderPhoto -> `ga_work_order_photos`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| workOrderId | String | `work_order_id` |
| filePath | String | `file_path` |
| type | GaWorkOrderPhotoType | `type` |
| caption | String? | `caption` |
| createdAt | DateTime | `created_at` |
| workOrder | GaWorkOrder | -> GaWorkOrder via `work_order_id` |

## GaWorkOrderPart -> `ga_work_order_parts`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| workOrderId | String | `work_order_id` |
| inventoryItemId | String | `inventory_item_id` |
| fromLocationId | String? | `from_location_id` |
| quantity | Decimal | `quantity` |
| unitCost | Decimal | `unit_cost` |
| inventoryMovementId | String? | `inventory_movement_id` |
| createdAt | DateTime | `created_at` |
| workOrder | GaWorkOrder | -> GaWorkOrder via `work_order_id` |
| inventoryItem | GaInventoryItem | -> GaInventoryItem via `inventory_item_id` |
| fromLocation | GaLocation | -> GaLocation via `from_location_id` |
| movement | GaInventoryMovement | -> GaInventoryMovement via `inventory_movement_id` |

## GaMaterialRequest -> `ga_material_requests`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| code | String | `code` |
| status | GaMaterialRequestStatus | `status` |
| workOrderId | String? | `work_order_id` |
| assetId | String? | `asset_id` |
| machineTarget | String? | `machine_target` |
| notes | String? | `notes` |
| requestedById | String | `requested_by_id` |
| requestedAt | DateTime | `requested_at` |
| approvedById | String? | `approved_by_id` |
| approvedAt | DateTime? | `approved_at` |
| rejectedById | String? | `rejected_by_id` |
| rejectedAt | DateTime? | `rejected_at` |
| rejectReason | String? | `reject_reason` |
| purchaseOrderId | String? | `purchase_order_id` |
| glAccountId | String? | `gl_account_id` |
| journalEntryId | String? | `journal_entry_id` |
| purchaseInvoiceId | String? | `purchase_invoice_id` |
| creditAccountId | String? | `credit_account_id` |
| paidById | String? | `paid_by_id` |
| paidAt | DateTime? | `paid_at` |
| paidAmount | Decimal? | `paid_amount` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |
| workOrder | GaWorkOrder | -> GaWorkOrder via `work_order_id` |
| asset | GaAsset | -> GaAsset via `asset_id` |
| items | GaMaterialRequestItem[] | <- GaMaterialRequestItem.materialRequest (list) |
| journalEntry | JournalEntry | -> JournalEntry via `journal_entry_id` |
| purchaseInvoice | PurchaseInvoice | -> PurchaseInvoice via `purchase_invoice_id` |
| glAccount | Account | -> Account via `gl_account_id` |
| creditAccount | Account | -> Account via `credit_account_id` |

## GaMaterialRequestItem -> `ga_material_request_items`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| materialRequestId | String | `material_request_id` |
| inventoryItemId | String? | `inventory_item_id` |
| itemName | String | `item_name` |
| sku | String? | `sku` |
| uom | String | `uom` |
| quantity | Decimal | `quantity` |
| notes | String? | `notes` |
| vendorName | String? | `vendor_name` |
| unitPrice | Decimal | `unit_price` |
| poNumber | String? | `po_number` |
| procurementStatus | GaProcurementStatus | `procurement_status` |
| receivedQty | Decimal | `received_qty` |
| receivedAt | DateTime? | `received_at` |
| receivedIntoLocationId | String? | `received_into_location_id` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |
| materialRequest | GaMaterialRequest | -> GaMaterialRequest via `material_request_id` |
| inventoryItem | GaInventoryItem | -> GaInventoryItem via `inventory_item_id` |
| receivedIntoLocation | GaLocation | -> GaLocation via `received_into_location_id` |

## GaVehicleData -> `ga_vehicle_data`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| assetId | String | `asset_id` |
| noPlat | String? | `no_plat` |
| brand | String? | `brand` |
| tipe | String? | `tipe` |
| tahunProduksi | Int? | `tahun_produksi` |
| noMesin | String? | `no_mesin` |
| noRangka | String? | `no_rangka` |
| jenisBBM | GaVehicleFuelType? | `jenis_bbm` |
| kapasitasTangki | Decimal? | `kapasitas_tangki` |
| warna | String? | `warna` |
| odometerSaatBeli | Int? | `odometer_saat_beli` |
| odometerTerakhir | Int? | `odometer_terakhir` |
| notes | String? | `notes` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |
| asset | GaAsset | -> GaAsset via `asset_id` |

## GaVehicleServiceLog -> `ga_vehicle_service_logs`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| assetId | String | `asset_id` |
| tanggal | DateTime | `tanggal` |
| odometer | Int? | `odometer` |
| jenisService | GaVehicleServiceType | `jenis_service` |
| deskripsi | String | `deskripsi` |
| bengkel | String? | `bengkel` |
| biaya | Decimal? | `biaya` |
| workOrderId | String? | `work_order_id` |
| journalEntryId | String? | `journal_entry_id` |
| purchaseInvoiceId | String? | `purchase_invoice_id` |
| accountId | String? | `account_id` |
| creditAccountId | String? | `credit_account_id` |
| source | String | `source` |
| externalRef | String? | `external_ref` |
| createdById | String? | `created_by_id` |
| createdAt | DateTime | `created_at` |
| asset | GaAsset | -> GaAsset via `asset_id` |
| workOrder | GaWorkOrder | -> GaWorkOrder via `work_order_id` |
| journalEntry | JournalEntry | -> JournalEntry via `journal_entry_id` |
| purchaseInvoice | PurchaseInvoice | -> PurchaseInvoice via `purchase_invoice_id` |
| account | Account | -> Account via `account_id` |
| creditAccount | Account | -> Account via `credit_account_id` |
| serviceRequest | GaVehicleServiceRequest | <- GaVehicleServiceRequest.serviceLog (1:1, field `serviceRequestRef`) |

## GaVehicleServiceRequest -> `ga_vehicle_service_requests`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| assetId | String | `asset_id` |
| requestedById | String | `requested_by_id` |
| jenisService | GaVehicleServiceType | `jenis_service` |
| odometer | Int? | `odometer` |
| deskripsi | String | `deskripsi` |
| urgensi | GaWorkOrderPriority | `urgensi` |
| status | GaVehicleServiceRequestStatus | `status` |
| approvedById | String? | `approved_by_id` |
| approvedAt | DateTime? | `approved_at` |
| rejectedReason | String? | `rejected_reason` |
| workOrderId | String? | `work_order_id` |
| serviceLogId | String? | `service_log_id` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |
| asset | GaAsset | -> GaAsset via `asset_id` |
| workOrder | GaWorkOrder | -> GaWorkOrder via `work_order_id` |
| serviceLog | GaVehicleServiceLog | -> GaVehicleServiceLog via `service_log_id` |

## GaVehicleTax -> `ga_vehicle_taxes`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| assetId | String | `asset_id` |
| type | GaVehicleTaxType | `type` |
| periodYear | Int | `period_year` |
| amount | Decimal? | `amount` |
| paidAt | DateTime? | `paid_at` |
| dueDate | DateTime | `due_date` |
| status | GaVehicleTaxStatus | `status` |
| documentId | String? | `document_id` |
| notes | String? | `notes` |
| createdById | String? | `created_by_id` |
| paidAmount | Decimal? | `paid_amount` |
| paidById | String? | `paid_by_id` |
| journalEntryId | String? | `journal_entry_id` |
| purchaseInvoiceId | String? | `purchase_invoice_id` |
| accountId | String? | `account_id` |
| creditAccountId | String? | `credit_account_id` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |
| asset | GaAsset | -> GaAsset via `asset_id` |
| document | GaDocument | -> GaDocument via `document_id` |
| request | GaVehicleTaxRequest | <- GaVehicleTaxRequest.tax (1:1, field `requestRef`) |
| journalEntry | JournalEntry | -> JournalEntry via `journal_entry_id` |
| purchaseInvoice | PurchaseInvoice | -> PurchaseInvoice via `purchase_invoice_id` |
| account | Account | -> Account via `account_id` |
| creditAccount | Account | -> Account via `credit_account_id` |

## GaVehicleTaxRequest -> `ga_vehicle_tax_requests`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| assetId | String | `asset_id` |
| requestedById | String | `requested_by_id` |
| type | GaVehicleTaxType | `type` |
| periodYear | Int | `period_year` |
| estimatedAmount | Decimal? | `estimated_amount` |
| dueDate | DateTime | `due_date` |
| deskripsi | String? | `deskripsi` |
| urgensi | GaWorkOrderPriority | `urgensi` |
| status | GaVehicleTaxRequestStatus | `status` |
| approvedById | String? | `approved_by_id` |
| approvedAt | DateTime? | `approved_at` |
| rejectedReason | String? | `rejected_reason` |
| taxId | String? | `tax_id` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |
| asset | GaAsset | -> GaAsset via `asset_id` |
| tax | GaVehicleTax | -> GaVehicleTax via `tax_id` |

## GaVehicleInsurance -> `ga_vehicle_insurances`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| assetId | String | `asset_id` |
| type | GaVehicleInsuranceType | `type` |
| provider | String | `provider` |
| policyNumber | String? | `policy_number` |
| premium | Decimal? | `premium` |
| coverageValue | Decimal? | `coverage_value` |
| startDate | DateTime | `start_date` |
| endDate | DateTime | `end_date` |
| status | GaVehicleInsuranceStatus | `status` |
| documentId | String? | `document_id` |
| notes | String? | `notes` |
| createdById | String? | `created_by_id` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |
| asset | GaAsset | -> GaAsset via `asset_id` |
| document | GaDocument | -> GaDocument via `document_id` |

## GaVehicleFuelLog -> `ga_vehicle_fuel_logs`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| assetId | String | `asset_id` |
| tanggal | DateTime | `tanggal` |
| odometer | Int? | `odometer` |
| liters | Decimal | `liters` |
| pricePerLiter | Decimal? | `price_per_liter` |
| totalCost | Decimal | `total_cost` |
| spbu | String? | `spbu` |
| driverName | String? | `driver_name` |
| notes | String? | `notes` |
| createdById | String? | `created_by_id` |
| createdAt | DateTime | `created_at` |
| asset | GaAsset | -> GaAsset via `asset_id` |

## GaVehicleInspection -> `ga_vehicle_inspections`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| assetId | String | `asset_id` |
| tanggal | DateTime | `tanggal` |
| odometer | Int? | `odometer` |
| checklist | Json | `checklist` |
| overallResult | GaVehicleInspectionResult | `overall_result` |
| notes | String? | `notes` |
| inspectorName | String? | `inspector_name` |
| inspectorUserId | String? | `inspector_user_id` |
| fuelLevel | GaVehicleFuelLevel? | `fuel_level` |
| nextDueDate | DateTime? | `next_due_date` |
| inspectedById | String? | `inspected_by_id` |
| createdAt | DateTime | `created_at` |
| asset | GaAsset | -> GaAsset via `asset_id` |
| photos | GaVehicleInspectionPhoto[] | <- GaVehicleInspectionPhoto.inspection (list) |

## GaVehicleInspectionPhoto -> `ga_vehicle_inspection_photos`
_Foto bukti visual per KATEGORI checklist inspeksi (mesin/kelistrikan/rem/ban/ keselamatan/lain). Satu inspeksi bisa punya beberapa foto, satu per kategori (tidak per sub-item — terlalu granular)._

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| inspectionId | String | `inspection_id` |
| category | String | `category` |
| filePath | String | `file_path` |
| caption | String? | `caption` |
| createdAt | DateTime | `created_at` |
| inspection | GaVehicleInspection | -> GaVehicleInspection via `inspection_id` |

## GaVehicleIncident -> `ga_vehicle_incidents`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| assetId | String | `asset_id` |
| tanggal | DateTime | `tanggal` |
| location | String? | `location` |
| driverName | String? | `driver_name` |
| description | String | `description` |
| severity | GaVehicleIncidentSeverity | `severity` |
| status | GaVehicleIncidentStatus | `status` |
| estimatedCost | Decimal? | `estimated_cost` |
| actualCost | Decimal? | `actual_cost` |
| insuranceClaimNo | String? | `insurance_claim_no` |
| reportedById | String? | `reported_by_id` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |
| asset | GaAsset | -> GaAsset via `asset_id` |

## GaVehicleTire -> `ga_vehicle_tires`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| assetId | String | `asset_id` |
| serialNumber | String | `serial_number` |
| brand | String | `brand` |
| model | String? | `model` |
| size | String? | `size` |
| position | GaVehicleTirePosition | `position` |
| installedAt | DateTime | `installed_at` |
| installedOdometer | Int? | `installed_odometer` |
| notes | String? | `notes` |
| status | GaVehicleTireStatus | `status` |
| createdById | String? | `created_by_id` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |
| asset | GaAsset | -> GaAsset via `asset_id` |

## GaVehicleOilLog -> `ga_vehicle_oil_logs`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| assetId | String | `asset_id` |
| tanggal | DateTime | `tanggal` |
| odometer | Int | `odometer` |
| nextOdometerDue | Int | `next_odometer_due` |
| brand | String | `brand` |
| volume | Decimal | `volume` |
| notes | String? | `notes` |
| createdById | String? | `created_by_id` |
| createdAt | DateTime | `created_at` |
| asset | GaAsset | -> GaAsset via `asset_id` |

## GaBuildingData -> `ga_building_data`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| assetId | String | `asset_id` |
| jenis | GaBuildingType | `jenis` |
| ownership | GaBuildingOwnership | `ownership` |
| luasTanah | Decimal? | `luas_tanah` |
| luasBangunan | Decimal? | `luas_bangunan` |
| jumlahLantai | Int? | `jumlah_lantai` |
| tahunBangun | Int? | `tahun_bangun` |
| tahunRenovasi | Int? | `tahun_renovasi` |
| alamatLengkap | String? | `alamat_lengkap` |
| kelurahan | String? | `kelurahan` |
| kecamatan | String? | `kecamatan` |
| kota | String? | `kota` |
| provinsi | String? | `provinsi` |
| kodePos | String? | `kode_pos` |
| certificateType | GaLandCertificateType? | `certificate_type` |
| certificateNumber | String? | `certificate_number` |
| njopTanah | Decimal? | `njop_tanah` |
| njopBangunan | Decimal? | `njop_bangunan` |
| notes | String? | `notes` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |
| asset | GaAsset | -> GaAsset via `asset_id` |

## GaBuildingRental -> `ga_building_rentals`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| assetId | String | `asset_id` |
| arah | GaBuildingRentalArah | `arah` |
| pihak | String | `pihak` |
| kontak | String? | `kontak` |
| nilaiSewa | Decimal | `nilai_sewa` |
| periode | GaBuildingRentalPeriode | `periode` |
| tanggalMulai | DateTime | `tanggal_mulai` |
| tanggalAkhir | DateTime | `tanggal_akhir` |
| status | GaBuildingRentalStatus | `status` |
| documentId | String? | `document_id` |
| notes | String? | `notes` |
| createdById | String? | `created_by_id` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |
| asset | GaAsset | -> GaAsset via `asset_id` |
| document | GaDocument | -> GaDocument via `document_id` |

## GaEquipmentData -> `ga_equipment_data`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| assetId | String | `asset_id` |
| maxCapacity | Decimal? | `max_capacity` |
| unit | GaWeighingUnit? | `unit` |
| accuracy | Decimal? | `accuracy` |
| classLabel | String? | `class_label` |
| defaultCalibrator | String? | `default_calibrator` |
| intervalMonths | Int | `interval_months` |
| lastCalibratedAt | DateTime? | `last_calibrated_at` |
| nextCalibrationDue | DateTime? | `next_calibration_due` |
| notes | String? | `notes` |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |
| asset | GaAsset | -> GaAsset via `asset_id` |

## GaCalibrationLog -> `ga_calibration_logs`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| assetId | String | `asset_id` |
| tanggal | DateTime | `tanggal` |
| lembaga | String | `lembaga` |
| noSertifikat | String | `no_sertifikat` |
| mode | GaCalibrationMode | `mode` |
| biaya | Decimal? | `biaya` |
| hasil | GaCalibrationResult | `hasil` |
| deviasi | String? | `deviasi` |
| rangeTested | String? | `range_tested` |
| validUntil | DateTime | `valid_until` |
| documentId | String? | `document_id` |
| workOrderId | String? | `work_order_id` |
| createdById | String? | `created_by_id` |
| createdAt | DateTime | `created_at` |
| asset | GaAsset | -> GaAsset via `asset_id` |
| document | GaDocument | -> GaDocument via `document_id` |
| workOrder | GaWorkOrder | -> GaWorkOrder via `work_order_id` |

## PaymentRequestType -> `payment_request_types`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| code | String | `code` |
| name | String | `name` |
| kind | String | `kind` |
| description | String? | `description` |
| defaultExpenseAccountId | String? | `default_expense_account_id` |
| defaultExpenseAccount | Account | -> Account via `default_expense_account_id` |
| requiresAttachment | Boolean | `requires_attachment` |
| requiresSettlement | Boolean | `requires_settlement` |
| isActive | Boolean | `is_active` |
| sortOrder | Int | `sort_order` |
| requests | PaymentRequest[] | <- PaymentRequest.type (list) |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## PaymentRequest -> `payment_requests`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| number | String | `number` |
| requesterId | String | `requester_id` |
| requester | User | -> User via `requester_id` |
| supervisorId | String? | `supervisor_id` |
| supervisor | User | -> User via `supervisor_id` |
| typeId | String | `type_id` |
| type | PaymentRequestType | -> PaymentRequestType via `type_id` |
| costCenterId | String? | `cost_center_id` |
| costCenter | GaCostCenter | -> GaCostCenter via `cost_center_id` |
| department | String? | `department` |
| payeeKind | String | `payee_kind` |
| partyId | String? | `party_id` |
| party | Party | -> Party via `party_id` |
| payeeName | String | `payee_name` |
| bankName | String? | `bank_name` |
| bankAccountNo | String? | `bank_account_no` |
| bankAccountHolder | String? | `bank_account_holder` |
| purpose | String | `purpose` |
| neededBy | DateTime? | `needed_by` |
| currency | String | `currency` |
| totalAmount | Decimal | `total_amount` |
| status | String | `status` |
| directorRole | String? | `director_role` |
| submittedAt | DateTime? | `submitted_at` |
| supervisorApprovedAt | DateTime? | `supervisor_approved_at` |
| financeApprovedAt | DateTime? | `finance_approved_at` |
| financeApprovedBy | String? | `finance_approved_by` |
| directorApprovedAt | DateTime? | `director_approved_at` |
| directorApprovedBy | String? | `director_approved_by` |
| approvedAt | DateTime? | `approved_at` |
| rejectedAt | DateTime? | `rejected_at` |
| lastNote | String? | `last_note` |
| cancelledAt | DateTime? | `cancelled_at` |
| paidAt | DateTime? | `paid_at` |
| paidById | String? | `paid_by` |
| paidBy | User | -> User via `paid_by` |
| paymentDate | DateTime? | `payment_date` |
| cashAccountId | String? | `cash_account_id` |
| cashAccount | Account | -> Account via `cash_account_id` |
| journalEntryId | String? | `journal_entry_id` |
| journalEntry | JournalEntry | -> JournalEntry via `journal_entry_id` |
| paymentReference | String? | `payment_reference` |
| travelDestination | String? | `travel_destination` |
| travelStartDate | DateTime? | `travel_start_date` |
| travelEndDate | DateTime? | `travel_end_date` |
| travelParticipants | String? | `travel_participants` |
| settlementStatus | String? | `settlement_status` |
| settledAt | DateTime? | `settled_at` |
| settlementJournalId | String? | `settlement_journal_id` |
| settlementJournal | JournalEntry | -> JournalEntry via `settlement_journal_id` |
| settlementSupervisorId | String? | `settlement_supervisor_id` |
| settlementSupervisor | User | -> User via `settlement_supervisor_id` |
| settlementNote | String? | `settlement_note` |
| settlementDeadline | DateTime? | `settlement_deadline` |
| settlementSubmittedAt | DateTime? | `settlement_submitted_at` |
| settlementSupervisorApprovedAt | DateTime? | `settlement_supervisor_approved_at` |
| settlementLastNote | String? | `settlement_last_note` |
| settlementDate | DateTime? | `settlement_date` |
| settlementRealized | Decimal? | `settlement_realized` |
| settlementCashAccountId | String? | `settlement_cash_account_id` |
| settlementCashAccount | Account | -> Account via `settlement_cash_account_id` |
| settlementReference | String? | `settlement_reference` |
| source | String | `source` |
| sourceRef | String? | `source_ref` |
| lines | PaymentRequestLine[] | <- PaymentRequestLine.paymentRequest (list) |
| settlementLines | PaymentRequestSettlementLine[] | <- PaymentRequestSettlementLine.paymentRequest (list) |
| attachments | PaymentRequestAttachment[] | <- PaymentRequestAttachment.paymentRequest (list) |
| history | PaymentRequestHistory[] | <- PaymentRequestHistory.paymentRequest (list) |
| createdAt | DateTime | `created_at` |
| updatedAt | DateTime | `updated_at` |

## PaymentRequestLine -> `payment_request_lines`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| paymentRequestId | String | `payment_request_id` |
| paymentRequest | PaymentRequest | -> PaymentRequest via `payment_request_id` |
| lineNo | Int | `line_no` |
| description | String | `description` |
| amount | Decimal | `amount` |
| expenseAccountId | String? | `expense_account_id` |
| expenseAccount | Account | -> Account via `expense_account_id` |
| costCenterId | String? | `cost_center_id` |
| costCenter | GaCostCenter | -> GaCostCenter via `cost_center_id` |
| expenseDate | DateTime? | `expense_date` |
| category | String? | `category` |
| quantity | Decimal? | `quantity` |
| unitAmount | Decimal? | `unit_amount` |

## PaymentRequestSettlementLine -> `payment_request_settlement_lines`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| paymentRequestId | String | `payment_request_id` |
| paymentRequest | PaymentRequest | -> PaymentRequest via `payment_request_id` |
| lineNo | Int | `line_no` |
| description | String | `description` |
| expenseDate | DateTime? | `expense_date` |
| amount | Decimal | `amount` |
| category | String? | `category` |
| plannedLineId | String? | `planned_line_id` |
| expenseAccountId | String? | `expense_account_id` |
| expenseAccount | Account | -> Account via `expense_account_id` |

## PaymentRequestAttachment -> `payment_request_attachments`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| paymentRequestId | String | `payment_request_id` |
| paymentRequest | PaymentRequest | -> PaymentRequest via `payment_request_id` |
| fileName | String | `file_name` |
| storedName | String | `stored_name` |
| mimeType | String | `mime_type` |
| fileSize | Int | `file_size` |
| filePath | String | `file_path` |
| uploadedBy | String | `uploaded_by` |
| scope | String | `scope` |
| createdAt | DateTime | `created_at` |

## PaymentRequestHistory -> `payment_request_history`

| field | tipe | kolom / relasi |
|---|---|---|
| id | String | `id` |
| paymentRequestId | String | `payment_request_id` |
| paymentRequest | PaymentRequest | -> PaymentRequest via `payment_request_id` |
| action | String | `action` |
| actorId | String | `actor_id` |
| actor | User | -> User via `actor_id` |
| note | String? | `note` |
| fromStatus | String? | `from_status` |
| toStatus | String? | `to_status` |
| createdAt | DateTime | `created_at` |

## Enum
- `AccountType`: ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE
- `RootType`: ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE
- `PartyType`: Customer, Supplier, Both
- `QuotationStatus`: Draft, Sent, Accepted, Rejected, Expired, Cancelled
- `SalesOrderStatus`: Draft, Submitted, PartiallyDelivered, FullyDelivered, Cancelled
- `InquiryStatus`: Baru, Diproses, Dikonversi, Hilang, Ditutup
- `InquiryChannel`: WhatsApp, Telepon, Email, WalkIn, Referral, Lainnya
- `DocumentStatus`: Draft, Submitted, Cancelled
- `InvoiceStatus`: Draft, Submitted, Paid, PartiallyPaid, Overdue, Cancelled
- `PaymentType`: Receive, Pay, VendorDeposit, CustomerDeposit, VendorRefund, CustomerRefund
- `StockMovementType`: In, Out, AdjustmentIn, AdjustmentOut, TransferOut, TransferIn
- `StockOpnameStatus`: Draft, Submitted, Cancelled
- `GaAssetStatus`: ACTIVE, IN_REPAIR, RETIRED, LOST
- `GaAssetCondition`: GOOD, FAIR, POOR, BROKEN
- `GaDocumentStatus`: ACTIVE, EXPIRED, REVOKED, ARCHIVED
- `GaInventoryType`: SPAREPART, CONSUMABLE
- `GaMovementType`: IN, OUT, TRANSFER, ADJUSTMENT
- `GaWorkOrderType`: PREVENTIVE, CORRECTIVE
- `GaWorkOrderStatus`: OPEN, ASSIGNED, IN_PROGRESS, DONE, CLOSED, CANCELLED
- `GaWorkOrderPriority`: LOW, MEDIUM, HIGH, URGENT
- `GaWorkOrderPhotoType`: BEFORE, AFTER
- `GaMaterialRequestStatus`: DRAFT, SUBMITTED, APPROVED, REJECTED, FULFILLED, PAID, CANCELLED
- `GaProcurementStatus`: REQUESTED, QUOTED, ORDERED, RECEIVED, CANCELLED
- `GaVehicleFuelType`: BENSIN, SOLAR, PERTAMAX, PERTALITE, DEX, LPG, LISTRIK, HYBRID, LAINNYA
- `GaVehicleServiceType`: OLI, TUNE_UP, SERVIS_BERKALA, PERBAIKAN, GANTI_BAN, ELEKTRIKAL, AC, LAINNYA
- `GaVehicleServiceRequestStatus`: SUBMITTED, APPROVED, REJECTED, COMPLETED, CANCELLED
- `GaVehicleTaxType`: STNK_TAHUNAN, STNK_5_TAHUNAN, KIR, PAJAK_KENDARAAN
- `GaVehicleTaxStatus`: ACTIVE, EXPIRED, IN_PROCESS, PAID
- `GaVehicleTaxRequestStatus`: SUBMITTED, APPROVED, REJECTED, COMPLETED, CANCELLED
- `GaVehicleInsuranceType`: ALL_RISK, TLO, KECELAKAAN_DIRI, LAINNYA
- `GaVehicleInsuranceStatus`: ACTIVE, EXPIRED, CANCELLED
- `GaVehicleInspectionResult`: BAIK, PERLU_PERHATIAN, TIDAK_LAYAK
- `GaVehicleFuelLevel`: KOSONG, SEPEREMPAT, SETENGAH, TIGA_PEREMPAT, PENUH
- `GaVehicleIncidentSeverity`: RINGAN, SEDANG, BERAT
- `GaVehicleIncidentStatus`: DILAPORKAN, DALAM_PROSES, SELESAI, KLAIM_ASURANSI
- `GaVehicleTirePosition`: DEPAN_KIRI, DEPAN_KANAN, BELAKANG_KIRI, BELAKANG_KANAN, BELAKANG_KIRI_DALAM, BELAKANG_KIRI_LUAR, BELAKANG_KANAN_DALAM, BELAKANG_KANAN_LUAR, CADANGAN
- `GaVehicleTireStatus`: ACTIVE, ARCHIVED, SPARE
- `GaBuildingType`: KANTOR, GUDANG, PABRIK, RUMAH, MESS, RUKO, LAINNYA
- `GaBuildingOwnership`: MILIK_SENDIRI, SEWA_MASUK, SEWA_KELUAR, PINJAM_PAKAI
- `GaLandCertificateType`: HM, HGB, HGU, HP_AKTA, GIRIK, BELUM_SERTIFIKAT
- `GaBuildingRentalArah`: SEWA_MASUK, SEWA_KELUAR
- `GaBuildingRentalPeriode`: BULANAN, TAHUNAN
- `GaBuildingRentalStatus`: AKTIF, BERAKHIR, DIBATALKAN
- `GaWeighingUnit`: KG, GRAM, TON, LBS
- `GaCalibrationResult`: PASS, FAIL, CONDITIONAL
- `GaCalibrationMode`: ON_SITE, SHIPPED

## Jasa maklon (tanpa entity JPA, `06_toll_service.sql`)

Tabel `toll_orders`, `customer_stocks`, `customer_stock_movements`, `customer_goods_returns`,
`customer_goods_return_items`, `service_item_party_rates` diakses lewat SQL native di paket `erp.toll`
(nama kolom snake_case; respons memakai alias camelCase).

