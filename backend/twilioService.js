import twilio from 'twilio';
import axios from 'axios';

const accountSid = process.env.TWILIO_ACCOUNT_SID;
const authToken = process.env.TWILIO_AUTH_TOKEN;
const twilioPhoneNumber = process.env.TWILIO_PHONE_NUMBER;

let client = null;
if (accountSid && authToken) {
    client = twilio(accountSid, authToken);
}

export async function sendSMSAlert(toPhoneNumber, location, mapsLink) {
    if (!client) {
        console.log('Twilio not configured, skipping SMS');
        return { success: false, error: 'Twilio not configured' };
    }
    try {
        const message = await client.messages.create({
            body: `🚨 EMERGENCY: Fall detected! User may need help.\n\nLocation: ${location}\nMaps: ${mapsLink}\n\nPlease check on them immediately.`,
            from: twilioPhoneNumber,
            to: toPhoneNumber
        });

        console.log(`SMS sent to ${toPhoneNumber}:`, message.sid);
        return { success: true, messageId: message.sid };
    } catch (error) {
        console.error('Error sending SMS:', error);
        return { success: false, error: error.message };
    }
}

export async function triggerVoiceCall(toPhoneNumber, mapsLink) {
    if (!client) {
        console.log('Twilio not configured, skipping voice call');
        return { success: false, error: 'Twilio not configured' };
    }
    try {
        const twiml = new twilio.twiml.VoiceResponse();
        twiml.say('A fall has been detected. The user is in need of assistance.');
        twiml.say(`Location information: ${mapsLink}`);
        twiml.say('Sending alert to emergency services.');

        const call = await client.calls.create({
            url: `${process.env.SERVER_URL}/api/twiml`,
            to: toPhoneNumber,
            from: twilioPhoneNumber
        });

        console.log(`Voice call initiated to ${toPhoneNumber}:`, call.sid);
        return { success: true, callId: call.sid };
    } catch (error) {
        console.error('Error triggering voice call:', error);
        return { success: false, error: error.message };
    }
}

export async function sendAlertsToContacts(contacts, location, mapsLink, eventId) {
    const results = [];

    for (const contact of contacts) {
        if (contact.is_active) {
            // Send SMS
            const smsResult = await sendSMSAlert(contact.phone_number, location, mapsLink);
            results.push({
                contactId: contact.id,
                contactName: contact.name,
                sms: smsResult
            });

            // Trigger voice call to first contact only
            if (contacts.indexOf(contact) === 0) {
                const callResult = await triggerVoiceCall(contact.phone_number, mapsLink);
                results[results.length - 1].call = callResult;
            }
        }
    }

    return results;
}

export function getTwilioWebhookResponse(statusCallback) {
    const twiml = new twilio.twiml.VoiceResponse();
    twiml.say('Your emergency alert has been acknowledged.');
    twiml.say('Stay calm. Help will arrive shortly.');
    return twiml.toString();
}
