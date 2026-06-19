# Firebase Firestore Setup Guide for BiteDash

This guide walks you through setting up Firebase Firestore for the BiteDash food delivery app.

## Prerequisites

- Firebase project: `bitedash-1e078`
- Access to [Firebase Console](https://console.firebase.google.com/)

---

## Step 1: Enable Firestore in Firebase Console

### 1.1 Navigate to Firestore

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project: **bitedash-1e078**
3. In the left sidebar, click **Build** → **Firestore Database**
4. Click **Create database**

### 1.2 Configure Database

1. **Start in production mode** (recommended for production) or **test mode** (for development)
2. Select a location closest to Zimbabwe:
   - `europe-west1` (London) - Good for Africa latency
   - `us-central` - Alternative option
4. Click **Enable**

---

## Step 2: Add Security Rules

### Option A: Using Firebase Console

1. In Firestore Database, click the **Rules** tab
2. Replace the default rules with the content from `firestore.rules` file in this project
3. Click **Publish**

### Option B: Using Firebase CLI

```bash
# Install Firebase CLI if not already installed
npm install -g firebase-tools

# Login to Firebase
firebase login

# Initialize Firestore (if not already done)
firebase init firestore

# Deploy rules
firebase deploy --only firestore:rules
```

---

## Step 3: Create Composite Indexes (Optional but Recommended)

For optimal query performance, create these composite indexes:

### Via Firebase Console:
1. Go to **Firestore Database** → **Indexes** tab
2. Add the following composite indexes:

| Collection | Fields | Query Scope |
|-----------|-------|-------------|
| orders | status ASC, createdAt DESC | Collection |
| orders | restaurantId ASC, createdAt DESC | Collection |
| orders | driverId ASC, createdAt DESC | Collection |
| restaurants | isActive ASC, displayOrder ASC | Collection |

### Via Firebase CLI:
Add to `firebase.json`:
```json
{
  "firestore": {
    "indexes": "firestore.indexes.json"
  }
}
```

Create `firestore.indexes.json`:
```json
{
  "indexes": [
    {
      "collectionGroup": "orders",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "status", "order": "ASCENDING" },
        { "fieldPath": "createdAt", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "orders",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "restaurantId", "order": "ASCENDING" },
        { "fieldPath": "createdAt", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "orders",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "driverId", "order": "ASCENDING" },
        { "fieldPath": "createdAt", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "restaurants",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "isActive", "order": "ASCENDING" },
        { "fieldPath": "displayOrder", "order": "ASCENDING" }
      ]
    }
  ]
}
```

---

## Step 4: Seed Initial Data (Optional)

For first-time setup, you can seed initial restaurants and drivers:

### Using a Cloud Function (Recommended)

Create `functions/src/seedData.ts`:
```typescript
import * as admin from 'firebase-admin';
import * as functions from 'firebase-functions';

admin.initializeApp();

const db = admin.firestore();

export const seedInitialData = functions.https.onCall(async (data, context) => {
  // Verify admin
  if (!context.auth?.token.admin) {
    throw new functions.https.HttpsError('permission-denied', 'Admin only');
  }

  const restaurants = [
    {
      id: 'chicken_inn_belgravia',
      name: 'Chicken Inn (Belgravia)',
      description: "Zimbabwe's favorite flame-grilled chicken.",
      rating: 4.7,
      deliveryTime: '15-25 min',
      deliveryFee: 2.00,
      category: 'Fast Food',
      location: 'Belgravia, Harare',
      imageKeyword: 'chicken',
      displayOrder: 0,
      isActive: true
    },
    // Add more restaurants...
  ];

  const drivers = [
    {
      id: 'driver_1',
      name: 'Tinashe M.',
      phone: '+263771234567',
      vehicle: 'Honda Cub',
      isActive: true,
      ecoCashNumber: '',
      oneMoneyNumber: ''
    },
    // Add more drivers...
  ];

  // Batch write restaurants
  const batch = db.batch();
  for (const restaurant of restaurants) {
    const ref = db.collection('restaurants').doc(restaurant.id);
    batch.set(ref, restaurant);
  }
  await batch.commit();

  // Batch write drivers
  const driverBatch = db.batch();
  for (const driver of drivers) {
    const ref = db.collection('drivers').doc(driver.id);
    driverBatch.set(ref, driver);
  }
  await driverBatch.commit();

  return { success: true, message: 'Data seeded successfully' };
});
```

### Manually via Firebase Console

1. Go to **Firestore** → **Data** tab
2. Click **Start collection** → Enter `restaurants`
3. Add documents following the `FirestoreRestaurant` model structure
4. Repeat for `drivers` collection

---

## Step 5: Verify Sync Works

### Test Data Sync in Android App

1. Ensure `google-services.json` is in `app/` directory
2. Verify the JSON has Firestore API enabled (it should if using Firebase Console)
3. Run the app and check Logcat for Firebase initialization:

```
D/FirebaseApp: Initialized
D/Firestore: Firestore Client initialized
```

### Quick Verification Script

Add this to your app's startup to verify connection:

```kotlin
// In your Application class or MainActivity
FirebaseFirestore.getInstance().enableNetwork()
    .addOnSuccessListener {
        Log.d("Firestore", "Network enabled successfully")
    }
    .addOnFailureListener { e ->
        Log.e("Firestore", "Failed to enable network", e)
    }
```

---

## Troubleshooting

### "Missing or insufficient permissions"
- Check that security rules allow your operations
- For testing, temporarily use permissive rules, then restore secure rules

### "Firebase App not initialized"
- Ensure `google-services.json` is in `app/` directory
- Verify the `com.google.gms.google-services` plugin is applied

### "No document found"
- Check that you're querying the correct collection
- Verify the document IDs match your queries

### "Index required"
- Click the link in the error message to create the index
- Or add indexes manually as described in Step 3

---

## Next Steps

After Firestore is set up:
1. Integrate `FirestoreService` into your ViewModel for real-time sync
2. Implement authentication with Firebase Auth (optional)
3. Set up Cloud Functions for automated payouts (see `ADMIN_TRANSACTION_SETUP.md`)
