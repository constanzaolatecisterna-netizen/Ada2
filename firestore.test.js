const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("profiles").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("routines").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      networkCode: "ALIANZA-HOGAR",
      activeProfileId: "prof_constanza",
      updatedAt: now,
    })
  );
});

test("UserNetworkConfig: owner create/get/update succeed; cross-user and extra keys fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      networkCode: "ALIANZA-HOGAR",
      activeProfileId: "prof_constanza",
      updatedAt: past,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      networkCode: "FAMILIA-2026",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      networkCode: "HIJACKED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      networkCode: "ALIANZA-HOGAR",
      activeProfileId: "prof_constanza",
      isAdmin: true,
      updatedAt: past,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      networkCode: "FUTURE",
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("FamilyProfile: owner create/list/partial update succeed; invalid persona, @DocumentId id, and cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  const profileRef = aliceDb.collection("users").doc(ALICE_UID).collection("profiles").doc("prof_constanza");
  await assertSucceeds(
    profileRef.set({
      ownerUid: ALICE_UID,
      name: "Constanza",
      role: "Creadora",
      colorHex: "#38BDF8",
      persona: "ada",
      availability: "Disponible",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("profiles").get()
  );

  await assertSucceeds(
    profileRef.update({
      persona: "jarvis",
      availability: "Ocupado",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertFails(
    profileRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("profiles").doc("prof_bad_id").set({
      id: "prof_bad_id",
      ownerUid: ALICE_UID,
      name: "Bad",
      role: "Familia",
      colorHex: "#38BDF8",
      persona: "ada",
      availability: "Disponible",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("profiles").doc("prof_bad_persona").set({
      ownerUid: ALICE_UID,
      name: "Bad",
      role: "Familia",
      colorHex: "#38BDF8",
      persona: "ultron",
      availability: "Disponible",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("profiles").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("profiles").doc("prof_constanza").delete());
});

test("Routines, Reminders, Contacts, Messages, Rules, Alerts, Webhooks: Kotlin-mirrored payloads succeed and cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 4000);
  const aliceUser = aliceDb.collection("users").doc(ALICE_UID);

  // Routine
  const rtRef = aliceUser.collection("routines").doc("rt_1");
  await assertSucceeds(
    rtRef.set({
      ownerUid: ALICE_UID,
      profileId: "prof_constanza",
      text: "Tomar agua y descansar la vista",
      done: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(rtRef.update({ done: true, updatedAt: new Date(Date.now() - 1000) }));
  await assertSucceeds(aliceUser.collection("routines").get());

  // Reminder
  const remRef = aliceUser.collection("reminders").doc("rem_1");
  await assertSucceeds(
    remRef.set({
      ownerUid: ALICE_UID,
      profileId: "prof_constanza",
      time: "20:30",
      text: "Revisar pendientes de mañana con ADA",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(aliceUser.collection("reminders").get());

  // Contact (empty phone allowed for default contacts)
  const cRef = aliceUser.collection("contacts").doc("c_1");
  await assertSucceeds(
    cRef.set({
      ownerUid: ALICE_UID,
      profileId: "prof_constanza",
      alias: "Mamá",
      phone: "",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(cRef.update({ phone: "+56912345678", updatedAt: new Date(Date.now() - 1000) }));

  // Message
  const msgRef = aliceUser.collection("messages").doc("msg_1");
  await assertSucceeds(
    msgRef.set({
      ownerUid: ALICE_UID,
      fromName: "ADA",
      toName: "Toda la Familia",
      text: "Bienvenidos a la Red Alianza.",
      time: "10:20",
      status: "Entregado",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(msgRef.update({ status: "Leído", updatedAt: new Date(Date.now() - 1000) }));

  // Rule (update forbidden)
  const ruleRef = aliceUser.collection("rules").doc("rule_1");
  await assertSucceeds(
    ruleRef.set({
      ownerUid: ALICE_UID,
      trigger: "routine_done",
      targetProfileId: "prof_mama",
      text: "Ya completé mi rutina",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(ruleRef.update({ text: "Modified", updatedAt: new Date(Date.now() - 1000) }));

  // Alert (update forbidden)
  const alertRef = aliceUser.collection("alerts").doc("alert_1");
  await assertSucceeds(
    alertRef.set({
      ownerUid: ALICE_UID,
      app: "WhatsApp",
      text: "Hola familia",
      time: "10:22",
      source: "native",
      createdAt: ts,
    })
  );
  await assertFails(alertRef.update({ text: "Tampered" }));

  // Webhook (URL validation)
  const whRef = aliceUser.collection("webhooks").doc("wh_1");
  await assertSucceeds(
    whRef.set({
      ownerUid: ALICE_UID,
      name: "Casa Webhook",
      url: "https://example.com/hook",
      active: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(whRef.update({ active: false, updatedAt: new Date(Date.now() - 1000) }));
  await assertFails(
    aliceUser.collection("webhooks").doc("wh_bad").set({
      ownerUid: ALICE_UID,
      name: "Bad URL",
      url: "ftp://example.com/hook",
      active: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Cross-user isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("routines").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("messages").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("alerts").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("webhooks").get());
});
