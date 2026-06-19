# BiteDash Admin Transaction Setup Guide

## Zimbabwe Mobile Money & Payment Integration

This guide covers setting up the admin transaction features for BiteDash, including restaurant earnings, driver payouts, and Zimbabwe mobile money integration (EcoCash, OneMoney, InnBucks, ZIPIT/CABS).

---

## Overview of Transaction Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                         BiteDash Platform                           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Customer Order (USD)                                               │
│         │                                                          │
│         ▼                                                          │
│  ┌─────────────────────────────────────────────────────────┐      │
│  │  Platform Escrow (BiteDash holds funds)                   │      │
│  │                                                          │      │
│  │  Total: $25.00                                          │      │
│  │    ├── Restaurant Earnings: $20.00 (minus platform fee)  │      │
│  │    ├── Driver Payout: $5.00 (delivery + tip)             │      │
│  │    └── Platform Fee: $2.50 (10%)                        │      │
│  └─────────────────────────────────────────────────────────┘      │
│         │                                                          │
│         ▼                                                          │
│  ┌─────────────────────────────────────────────────────────┐      │
│  │  Payout Settlement (Scheduled)                           │      │
│  │                                                          │      │
│  │  Restaurant → EcoCash Merchant API → Bank Transfer      │      │
│  │  Driver     → EcoCash P2P        → Mobile Wallet        │      │
│  └─────────────────────────────────────────────────────────┘      │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Step 1: Configure Admin Settings in Firestore

### Access Admin Settings

1. Go to Firebase Console → Firestore
2. Navigate to `admin_settings` collection
3. Create document with ID: `admin_settings`

### Settings Document Structure

```json
{
  "id": "admin_settings",
  "payoutSchedule": "Weekly",
  "platformFeePercent": 10.0,
  "minimumPayoutThreshold": 10.0,
  "ecoCashMerchantId": "YOUR_ECOCASH_MERCHANT_ID",
  "oneMoneyMerchantId": "YOUR_ONEMONEY_MERCHANT_ID",
  "innBucksApiKey": "YOUR_INNBUCKS_API_KEY",
  "isPayoutAutomationEnabled": false,
  "updatedAt": null
}
```

### Configuration Options

| Setting | Description | Example |
|---------|-------------|---------|
| `payoutSchedule` | When payouts are processed | `Daily`, `Weekly`, `BiWeekly`, `Monthly` |
| `platformFeePercent` | Platform commission % | `10.0` for 10% |
| `minimumPayoutThreshold` | Min amount before payout | `10.0` USD |
| `ecoCashMerchantId` | EcoCash merchant credentials | `BD000123` |
| `oneMoneyMerchantId` | OneMoney merchant credentials | `OM456789` |
| `isPayoutAutomationEnabled` | Use Cloud Functions for auto-payout | `false` (manual initially) |

---

## Step 2: Set Up Restaurant Payout Information

### For Each Restaurant, Store:

```json
{
  "id": "chicken_inn_belgravia",
  "name": "Chicken Inn (Belgravia)",
  "ownerUsername": "owner",
  "ownerPassword": "password",
  "payoutInfo": {
    "ecoCashNumber": "+263772123456",
    "bankName": "Standard Chartered",
    "bankAccount": "1234567890",
    "bankBranch": "Harare Main",
    "paymentPreference": "ECO_CASH"
  }
}
```

### Collect Payout Info from Restaurant Owners

1. Add payout info collection form in the Restaurant Owner Dashboard
2. Validate mobile money numbers format: `+26377XXXXXXXX` or `077XXXXXXXX`
3. Store securely in Firestore

---

## Step 3: Set Up Driver Payout Information

### For Each Driver, Store:

```json
{
  "id": "driver_001",
  "name": "Tinashe M.",
  "phone": "+263771234567",
  "vehicle": "Honda Cub",
  "ecoCashNumber": "+263771234567",
  "oneMoneyNumber": "",
  "totalDeliveries": 0,
  "totalEarnings": 0.0,
  "pendingPayout": 0.0
}
```

### Driver Payout Calculation

```
Driver Payout = Delivery Fee + Customer Tip
              = $2.00 + $3.00
              = $5.00 per delivery
```

---

## Step 4: Mobile Money Integration

### EcoCash Integration

EcoCash is Zimbabwe's largest mobile money platform. Integration requires:

1. **Merchant Account Application**
   - Visit Econet service center with:
     - Business registration certificate
     - BIT (Buyer Identification Number)
     - Bank statements (3 months)
     - Physical address proof
   
2. **API Access**
   - Contact EcoCash Business Solutions
   - Request sandbox and production API credentials
   - Implement callback/webhook for payment confirmations

3. **Sample Integration Code** (Cloud Function):

```typescript
// functions/src/payments/ecoCash.ts
import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';

const ECO_CASH_API_URL = 'https://api.ecocash.co.zw/v1';
const MERCHANT_ID = functions.config().ecocash.merchant_id;
const API_KEY = functions.config().ecocash.api_key;

interface EcoCashPayoutRequest {
  recipientNumber: string;
  amount: number;
  reference: string;
  callbackUrl: string;
}

export async function sendEcoCashPayout(request: EcoCashPayoutRequest): Promise<{
  success: boolean;
  transactionRef?: string;
  error?: string;
}> {
  try {
    const response = await fetch(`${ECO_CASH_API_URL}/disburse`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${API_KEY}`,
        'X-Merchant-Id': MERCHANT_ID
      },
      body: JSON.stringify({
        recipient: request.recipientNumber.replace('+263', '263'),
        amount: request.amount,
        reference: request.reference,
        callback: request.callbackUrl
      })
    });

    const data = await response.json();
    
    if (response.ok) {
      return {
        success: true,
        transactionRef: data.transactionId
      };
    } else {
      return {
        success: false,
        error: data.message || 'EcoCash payout failed'
      };
    }
  } catch (error) {
    return {
      success: false,
      error: error.message
    };
  }
}
```

### OneMoney Integration

Similar to EcoCash:

1. **Merchant Registration**: Contact NetOne OneMoney Business
2. **API Access**: Request API credentials for bulk disbursements
3. **Implementation**: Similar to EcoCash with OneMoney-specific endpoints

### InnBucks Integration

InnBucks uses USSD-based settlement:

1. **Account Setup**: Register as InnBucks merchant
2. **USSD Integration**: For instant settlements via USSD codes
3. **API Access**: Bulk payment API for automated disbursements

### ZIPIT / CABS Bank Integration

For larger payouts or formal bank transfers:

1. **Bank Setup**: Open merchant account with CABS Bank
2. **ZIPIT Setup**: Request ZIPIT Bulk payment facility
3. **File-Based**: Submit CSV files for batch transfers

```typescript
// Sample ZIPIT bulk payment format
const zipitBulkPayment = {
  bankCode: 'CABS',
  accountNumber: '1234567890',
  transactions: [
    {
      id: 'TX001',
      recipientName: 'Restaurant Name',
      accountNumber: '1234567890',
      amount: 20.00,
      reference: 'BD-ORDER-001'
    }
  ]
};
```

---

## Step 5: Implement Automated Payout Cloud Function

### Create the Payout Cloud Function

```typescript
// functions/src/payouts/processPayouts.ts
import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import { sendEcoCashPayout } from '../payments/ecoCash';

const db = admin.firestore();

interface PayoutItem {
  type: 'RESTAURANT' | 'DRIVER';
  recipientId: string;
  recipientName: string;
  ecoCashNumber?: string;
  bankAccount?: string;
  amount: number;
  orderId: string;
}

export const processPayouts = functions.https.onCall(async (data, context) => {
  // Verify admin
  if (!context.auth?.token.admin) {
    throw new functions.https.HttpsError('permission-denied', 'Admin only');
  }

  const payoutItems: PayoutItem[] = data.payoutItems;

  const results = [];
  for (const item of payoutItems) {
    try {
      // Skip if below minimum threshold
      const settings = await db.collection('admin_settings').doc('admin_settings').get();
      const minThreshold = settings.data()?.minimumPayoutThreshold || 10.0;
      
      if (item.amount < minThreshold) {
        results.push({
          ...item,
          status: 'SKIPPED',
          reason: `Below minimum threshold (${minThreshold} USD)`
        });
        continue;
      }

      // Process EcoCash payout
      if (item.ecoCashNumber) {
        const result = await sendEcoCashPayout({
          recipientNumber: item.ecoCashNumber,
          amount: item.amount,
          reference: `BD-${item.type}-${item.orderId}`,
          callbackUrl: `https://bitedash.firebaseapp.com/api/payout-callback`
        });

        if (result.success) {
          // Record successful payout
          await db.collection('transactions').add({
            type: item.type === 'RESTAURANT' ? 'RESTAURANT_PAYOUT' : 'DRIVER_PAYOUT',
            recipientId: item.recipientId,
            recipientName: item.recipientName,
            amount: item.amount,
            paymentChannel: 'ECO_CASH',
            mobileMoneyRef: result.transactionRef,
            status: 'COMPLETED',
            orderId: item.orderId,
            createdAt: admin.firestore.FieldValue.serverTimestamp()
          });

          results.push({
            ...item,
            status: 'SUCCESS',
            transactionRef: result.transactionRef
          });
        } else {
          results.push({
            ...item,
            status: 'FAILED',
            reason: result.error
          });
        }
      } else if (item.bankAccount) {
        // Process ZIPIT/CABS bank transfer
        // Implementation similar to EcoCash
        results.push({
          ...item,
          status: 'PENDING',
          reason: 'Bank transfer queued'
        });
      }
    } catch (error) {
      results.push({
        ...item,
        status: 'ERROR',
        reason: error.message
      });
    }
  }

  // Update pending payout totals
  for (const result of results) {
    if (result.status === 'SUCCESS') {
      if (result.type === 'DRIVER') {
        await db.collection('drivers').doc(result.recipientId)
          .update({
            pendingPayout: admin.firestore.FieldValue.increment(-result.amount)
          });
      }
    }
  }

  return { results };
});
```

---

## Step 6: Admin Dashboard Integration

### Earnings Summary View

The admin dashboard displays:

1. **Total Platform Holding**
   - Sum of all unsettled order amounts
   
2. **Restaurant Earnings**
   - Per-restaurant pending payouts
   - Total platform-to-restaurant flow

3. **Driver Payouts**
   - Per-driver pending payouts
   - Delivery fees + tips breakdown

4. **Payout Schedule**
   - Current schedule (Daily/Weekly/etc.)
   - Next scheduled payout date

### Trigger Payout Settlement

```kotlin
// In BiteDashViewModel
fun triggerPayoutSettlement() {
    viewModelScope.launch {
        _isPayoutInProgress.value = true
        
        // 1. Get all unsettled completed orders
        val unsettledOrders = orderHistory.value.filter { 
            it.status == "COMPLETED" && !it.isSettled 
        }
        
        // 2. Calculate payouts
        val restaurantPayouts = unsettledOrders.groupBy { it.restaurantName }
            .mapValues { (_, orders) -> 
                orders.sumOf { it.totalCost * 0.90 } // 90% to restaurant (10% platform fee)
            }
        
        val driverPayouts = unsettledOrders
            .filter { it.driverId != null }
            .groupBy { it.driverId }
            .mapValues { (_, orders) ->
                orders.sumOf { it.deliveryFee + it.driverTip }
            }
        
        // 3. Process via Cloud Function
        // Call processPayouts Cloud Function
        
        // 4. Mark orders as settled
        repository.markCompletedOrdersAsSettled()
        
        _isPayoutInProgress.value = false
    }
}
```

---

## Step 7: Testing the Integration

### Test Scenarios

1. **Complete Order Flow**
   - Place order as customer
   - Accept order as restaurant
   - Mark ready for pickup
   - Claim as driver
   - Mark delivered
   - Verify in admin escrow ledger

2. **Payout Processing**
   - Create test payouts
   - Verify calculation accuracy
   - Test failure scenarios

3. **Mobile Money API**
   - Use sandbox/test credentials
   - Verify callback handling
   - Check transaction records

### Sandbox Environment

Request sandbox credentials from:
- EcoCash Business: business@econet.co.zw
- OneMoney: business@netone.co.zw
- InnBucks: api@innbucks.co.zw

---

## Step 8: Production Deployment Checklist

- [ ] EcoCash merchant account activated
- [ ] Production API credentials secured
- [ ] Bank/ZIPIT facility established
- [ ] Security rules tested
- [ ] Cloud Function monitoring enabled
- [ ] Error alerts configured
- [ ] Transaction audit logs verified
- [ ] Payout reconciliation process documented

---

## Support Contacts (Zimbabwe)

| Service | Contact |
|---------|---------|
| EcoCash Business | business@econet.co.zw / +263 242 700 800 |
| OneMoney Business | business@netone.co.zw / +263 242 700 200 |
| InnBucks API | api@innbucks.co.zw |
| CABS Bank ZIPIT | corporates@cabsbank.co.zw |

---

## API Rate Limits & Considerations

| Service | Rate Limit | Notes |
|---------|------------|-------|
| EcoCash Bulk | 1000 txns/day | Require pre-funding |
| OneMoney Bulk | 500 txns/day | Similar to EcoCash |
| InnBucks | Real-time | USSD-based, instant |
| ZIPIT | Per file limit | Next-day settlement |

---

## Summary

For BiteDash to process payouts in Zimbabwe:

1. **EcoCash is recommended** for most restaurant and driver payouts due to:
   - Wide coverage (urban and rural)
   - Instant P2P settlement
   - Lower transaction fees for smaller amounts

2. **ZIPIT/CABS** recommended for:
   - Larger restaurant payouts
   - Formal business settlements
   - Monthly batch processing

3. **Start with manual** payout processing:
   - Use admin dashboard to trigger settlements
   - Verify amounts before sending
   - Scale to automation once volume increases
