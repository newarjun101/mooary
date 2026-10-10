import { initializeApp } from "firebase-admin/app";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { onDocumentCreated } from "firebase-functions/v2/firestore";

initializeApp();

const db = getFirestore();
const MAX_TITLE_LENGTH = 120;
const MAX_BODY_LENGTH = 1_000;
const FCM_BATCH_SIZE = 500;

type NotificationPayload = { title: string; body: string };

export const sendFirestoreNotification = onDocumentCreated(
  { document: "outbound-notifications/{notificationId}", region: "asia-southeast1" },
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;

    const payload = readPayload(snapshot.data());
    if (!payload) {
      await snapshot.ref.update({
        status: "rejected",
        completedAt: FieldValue.serverTimestamp(),
        failure: "title and body must be non-empty strings within the supported length",
      });
      return;
    }

    const registrations = await db.collection("register-device").get();
    const devices = registrations.docs.flatMap((document) => {
      const token = document.get("token");
      return typeof token === "string" && token.length > 0 ? [{ document, token }] : [];
    });
    let sentCount = 0;
    let failedCount = 0;
    const staleRegistrations = [] as FirebaseFirestore.DocumentReference[];

    for (const chunk of chunks(devices, FCM_BATCH_SIZE)) {
      const response = await getMessaging().sendEachForMulticast({
        tokens: chunk.map(({ token }) => token),
        data: payload,
        android: { priority: "high" },
      });
      sentCount += response.successCount;
      failedCount += response.failureCount;
      response.responses.forEach((result, index) => {
        if (!result.success && isInvalidRegistration(result.error?.code)) {
          staleRegistrations.push(chunk[index].document.ref);
        }
      });
    }

    await Promise.all(staleRegistrations.map((reference) => reference.delete()));
    await snapshot.ref.update({
      status: failedCount === 0 ? "sent" : "completed-with-failures",
      sentCount,
      failedCount,
      removedTokenCount: staleRegistrations.length,
      completedAt: FieldValue.serverTimestamp(),
    });
  },
);

function readPayload(value: FirebaseFirestore.DocumentData): NotificationPayload | null {
  const { title, body } = value;
  if (
    typeof title !== "string" || typeof body !== "string" ||
    title.trim().length === 0 || body.trim().length === 0 ||
    title.length > MAX_TITLE_LENGTH || body.length > MAX_BODY_LENGTH
  ) return null;
  return { title, body };
}

function isInvalidRegistration(code: string | undefined): boolean {
  return code === "messaging/registration-token-not-registered" ||
    code === "messaging/invalid-registration-token";
}

function chunks<T>(values: T[], size: number): T[][] {
  const result: T[][] = [];
  for (let start = 0; start < values.length; start += size) result.push(values.slice(start, start + size));
  return result;
}
