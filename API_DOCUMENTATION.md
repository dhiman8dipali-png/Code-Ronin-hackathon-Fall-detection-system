# Fall Detection System - API Documentation

The Fall Detection Backend API provides endpoints for managing fall detection events, emergency contacts, and triggering alerts via the **Twilio Offline Port**.

### Twilio Offline Port (Native Proxy)
The application uses a specialized "port" of the Twilio API that operates in two modes:
1. **Online (Cloud Mode)**: Standard HTTP POST requests to Twilio for SMS and Voice.
2. **Offline (Native Mode)**: Direct hardware interaction via `android.telephony.SmsManager` to bypass the need for internet.

**Base URL:** `http://localhost:5000/api` (or your production URL)

---

## Authentication
Currently, the API uses job-based authentication. For production, implement JWT or API Keys.

---

## Error Response Format
All error responses follow this format:

```json
{
  "success": false,
  "error": "Descriptive error message"
}
```

---

## Endpoints

### 1. Health Check
**Endpoint:** `GET /api/health`

Check if the backend server is running and healthy.

**Response (200):**
```json
{
  "success": true,
  "status": "Server is running",
  "timestamp": "2024-03-21T14:30:00.000Z"
}
```

---

### 2. Fall Detection Alerts

#### 2.1 Report Fall Detection
**Endpoint:** `POST /api/alert`

Report a detected fall event from the Android app. This endpoint:
- Saves the fall event to the database
- Retrieves emergency contacts for the user
- Sends SMS & voice alerts via Twilio
- Returns confirmation of alerts sent

**Request Headers:**
```
Content-Type: application/json
```

**Request Body:**
```json
{
  "timestamp": 1711000000000,
  "latitude": 37.7749,
  "longitude": -122.4194,
  "confidence": 0.85,
  "accelerationMagnitude": 35.5,
  "gyroscopeMagnitude": 4.2,
  "tiltAngle": 65,
  "mapsLink": "https://www.google.com/maps/search/?api=1&query=37.7749,-122.4194",
  "userId": "user123"
}
```

**Response (200):**
```json
{
  "success": true,
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "Fall event recorded and alerts sent",
  "alertsSent": 3,
  "details": [
    {
      "contactId": "uuid",
      "contactName": "Jane Doe",
      "sms": {
        "success": true,
        "messageId": "SM1234567890abcdef"
      },
      "call": {
        "success": true,
        "callId": "CA1234567890abcdef"
      }
    },
    {
      "contactId": "uuid",
      "contactName": "John Smith",
      "sms": {
        "success": true,
        "messageId": "SM1234567890abcde0"
      }
    }
  ]
}
```

**Error Response (400):**
```json
{
  "success": false,
  "error": "Missing required fields: latitude, longitude, confidence"
}
```

**cURL Example:**
```bash
curl -X POST http://localhost:5000/api/alert \
  -H "Content-Type: application/json" \
  -d '{
    "timestamp": 1711000000000,
    "latitude": 37.7749,
    "longitude": -122.4194,
    "confidence": 0.85,
    "accelerationMagnitude": 35.5,
    "gyroscopeMagnitude": 4.2,
    "tiltAngle": 65,
    "mapsLink": "https://www.google.com/maps/...",
    "userId": "user123"
  }'
```

---

#### 2.2 Get Fall Detection Logs
**Endpoint:** `GET /api/logs`

Retrieve all fall detection events for a user with pagination.

**Query Parameters:**
- `userId` (string, optional): Filter logs by user ID. Default: "anonymous"
- `limit` (integer, optional): Number of records per page. Default: 50, Max: 500
- `offset` (integer, optional): Pagination offset. Default: 0

**Response (200):**
```json
{
  "success": true,
  "logs": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "timestamp": 1711000000000,
      "latitude": 37.7749,
      "longitude": -122.4194,
      "confidence": 0.85,
      "acceleration_magnitude": 35.5,
      "gyroscope_magnitude": 4.2,
      "tilt_angle": 65,
      "sos_triggered": 1,
      "maps_link": "https://www.google.com/maps/search/?api=1&query=37.7749,-122.4194",
      "user_id": "user123",
      "created_at": 1711000000
    },
    {
      "id": "660e8400-e29b-41d4-a716-446655440001",
      "timestamp": 1710999000000,
      "latitude": 37.7758,
      "longitude": -122.4185,
      "confidence": 0.72,
      "acceleration_magnitude": 28.3,
      "gyroscope_magnitude": 3.1,
      "tilt_angle": 52,
      "sos_triggered": 0,
      "maps_link": "https://www.google.com/maps/search/?api=1&query=37.7758,-122.4185",
      "user_id": "user123",
      "created_at": 1710999000
    }
  ],
  "pagination": {
    "limit": 50,
    "offset": 0,
    "total": 152
  }
}
```

**cURL Example:**
```bash
curl "http://localhost:5000/api/logs?userId=user123&limit=20&offset=0"
```

---

#### 2.3 Get Specific Fall Event
**Endpoint:** `GET /api/logs/:eventId`

Retrieve details for a specific fall event.

**Path Parameters:**
- `eventId` (string): The unique event ID

**Response (200):**
```json
{
  "success": true,
  "event": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "timestamp": 1711000000000,
    "latitude": 37.7749,
    "longitude": -122.4194,
    "confidence": 0.85,
    "acceleration_magnitude": 35.5,
    "gyroscope_magnitude": 4.2,
    "tilt_angle": 65,
    "sos_triggered": 1,
    "maps_link": "https://www.google.com/maps/search/?api=1&query=37.7749,-122.4194",
    "user_id": "user123",
    "created_at": 1711000000
  }
}
```

**Error Response (404):**
```json
{
  "success": false,
  "error": "Event not found"
}
```

**cURL Example:**
```bash
curl "http://localhost:5000/api/logs/550e8400-e29b-41d4-a716-446655440000"
```

---

#### 2.4 Update Fall Event
**Endpoint:** `PUT /api/logs/:eventId`

Update a fall event (e.g., mark SOS as triggered).

**Path Parameters:**
- `eventId` (string): The unique event ID

**Request Body:**
```json
{
  "sosTriggered": true
}
```

**Response (200):**
```json
{
  "success": true,
  "message": "Event updated"
}
```

**cURL Example:**
```bash
curl -X PUT http://localhost:5000/api/logs/550e8400-e29b-41d4-a716-446655440000 \
  -H "Content-Type: application/json" \
  -d '{"sosTriggered": true}'
```

---

#### 2.5 Delete Fall Event
**Endpoint:** `DELETE /api/logs/:eventId`

Delete a fall event from the database.

**Path Parameters:**
- `eventId` (string): The unique event ID

**Response (200):**
```json
{
  "success": true,
  "message": "Event deleted"
}
```

**cURL Example:**
```bash
curl -X DELETE http://localhost:5000/api/logs/550e8400-e29b-41d4-a716-446655440000
```

---

### 3. Emergency Contacts

#### 3.1 Add Emergency Contact
**Endpoint:** `POST /api/contacts`

Add a new emergency contact for a user.

**Request Body:**
```json
{
  "name": "Jane Doe",
  "phoneNumber": "+14155552671",
  "email": "jane.doe@example.com",
  "userId": "user123"
}
```

**Response (200):**
```json
{
  "success": true,
  "contactId": "550e8400-e29b-41d4-a716-446655440010",
  "message": "Contact added"
}
```

**Error Response (400):**
```json
{
  "success": false,
  "error": "Missing required fields: name, phoneNumber"
}
```

**cURL Example:**
```bash
curl -X POST http://localhost:5000/api/contacts \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Jane Doe",
    "phoneNumber": "+14155552671",
    "email": "jane.doe@example.com",
    "userId": "user123"
  }'
```

---

#### 3.2 Get All Emergency Contacts
**Endpoint:** `GET /api/contacts`

Retrieve all emergency contacts for a user.

**Query Parameters:**
- `userId` (string, optional): Filter contacts by user ID. Default: "anonymous"

**Response (200):**
```json
{
  "success": true,
  "contacts": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440010",
      "user_id": "user123",
      "name": "Jane Doe",
      "phone_number": "+14155552671",
      "email": "jane.doe@example.com",
      "is_active": 1,
      "created_at": 1711000000
    },
    {
      "id": "550e8400-e29b-41d4-a716-446655440011",
      "user_id": "user123",
      "name": "John Smith",
      "phone_number": "+14155552672",
      "email": "john.smith@example.com",
      "is_active": 1,
      "created_at": 1710999000
    }
  ]
}
```

**cURL Example:**
```bash
curl "http://localhost:5000/api/contacts?userId=user123"
```

---

#### 3.3 Update Emergency Contact
**Endpoint:** `PUT /api/contacts/:contactId`

Update an emergency contact's details or activation status.

**Path Parameters:**
- `contactId` (string): The unique contact ID

**Request Body (all fields optional):**
```json
{
  "name": "Jane Smith",
  "phoneNumber": "+14155552671",
  "email": "jane.smith@example.com",
  "isActive": true
}
```

**Response (200):**
```json
{
  "success": true,
  "message": "Contact updated"
}
```

**cURL Example:**
```bash
curl -X PUT http://localhost:5000/api/contacts/550e8400-e29b-41d4-a716-446655440010 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Jane Smith",
    "isActive": true
  }'
```

---

#### 3.4 Delete Emergency Contact
**Endpoint:** `DELETE /api/contacts/:contactId`

Delete an emergency contact.

**Path Parameters:**
- `contactId` (string): The unique contact ID

**Response (200):**
```json
{
  "success": true,
  "message": "Contact deleted"
}
```

**cURL Example:**
```bash
curl -X DELETE http://localhost:5000/api/contacts/550e8400-e29b-41d4-a716-446655440010
```

---

## Data Models

### Fall Event Object
```json
{
  "id": "uuid",
  "timestamp": 1711000000000,
  "latitude": 37.7749,
  "longitude": -122.4194,
  "confidence": 0.85,
  "acceleration_magnitude": 35.5,
  "gyroscope_magnitude": 4.2,
  "tilt_angle": 65,
  "sos_triggered": 0,
  "maps_link": "https://www.google.com/maps/...",
  "user_id": "user123",
  "created_at": 1711000000
}
```

### Emergency Contact Object
```json
{
  "id": "uuid",
  "user_id": "user123",
  "name": "Jane Doe",
  "phone_number": "+14155552671",
  "email": "jane.doe@example.com",
  "is_active": 1,
  "created_at": 1711000000
}
```

### Alert Result Object
```json
{
  "contactId": "uuid",
  "contactName": "Jane Doe",
  "sms": {
    "success": true,
    "messageId": "SMxxxxxxxxxxxxxxx"
  },
  "call": {
    "success": true,
    "callId": "CAxxxxxxxxxxxxxxx"
  }
}
```

---

## Status Codes

| Code | Meaning | Description |
|------|---------|-------------|
| 200 | OK | Request successful |
| 400 | Bad Request | Missing or invalid parameters |
| 404 | Not Found | Resource not found |
| 500 | Server Error | Internal server error |

---

## Rate Limiting
Currently, no rate limiting is implemented. For production:
- Implement 100 requests per minute per user/IP
- Queue SMS/call requests to prevent Twilio rate limits

---

## Best Practices

### Request Format
- Always include `Content-Type: application/json` header for POST/PUT requests
- Use proper phone number format: `+1XXXXXXXXXX`
- Include `userId` for multi-user support

### Error Handling
- Check `success` field before processing response
- Extract error message from `error` field
- Implement exponential backoff for retries

### Data Validation
- Validate GPS coordinates: -90 ≤ latitude ≤ 90, -180 ≤ longitude ≤ 180
- Validate confidence: 0 ≤ confidence ≤ 1
- Phone numbers must start with +1-999

### Privacy & Security
- Don't log sensitive user data
- Use HTTPS in production
- Implement user authentication
- Encrypt sensitive contact information

---

## Example Workflow

### Complete Fall Detection Flow

1. **User falls and is detected by app**
   ```
   POST /api/alert
   Body: {
     "latitude": 37.7749,
     "longitude": -122.4194,
     "confidence": 0.85,
     "userId": "user123"
   }
   ```

2. **Backend records event and sends alerts**
   ```
   Response: {
     "eventId": "xxx",
     "alertsSent": 2
   }
   ```

3. **App retrieves logs to show user**
   ```
   GET /api/logs?userId=user123
   ```

4. **User can update contact status**
   ```
   GET /api/contacts?userId=user123
   PUT /api/contacts/{id}
   ```

---

## Offline Messaging Protocol

When a fall is detected and the device is offline, the system initiates the following **Offline Sync Protocol**:

1. **Connectivity Check**: The `isNetworkAvailable()` method checks for WiFi/Cellular data.
2. **API Mocking**: If no network is found, the system redirects the request from the Twilio Cloud API to the **TwilioOfflinePort** native handler.
3. **Hardware Dispatch**: The message is dispatched via the device's GSM/LTE radio directly to the emergency contact's cellular number.
4. **Log Synchronization**: Once the device regains internet access, the local database logs are synchronized with the backend.

---

## Support
For issues or questions, check SETUP_INSTRUCTIONS.md or contact the development team.

