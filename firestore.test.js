const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_flux_123";
const BOB_UID = "bob_flux_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 2000);
}

function validSitePayload(uid) {
  const ts = pastTimestamp();
  return {
    userId: uid,
    title: "Nova Coffee Roasters",
    prompt: "Dark obsidian specialty coffee landing page",
    category: "landing",
    htmlContent: "<html><body><h1>Nova Coffee</h1></body></html>",
    summary: "Hero section, origin grid, roast subscription CTA",
    subdomain: "nova-coffee",
    hostingTier: "free",
    deployStatus: "draft",
    deployedUrl: "",
    customDomain: "",
    sslEnabled: true,
    deployCount: 0,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validDeploymentPayload(uid, siteId) {
  return {
    userId: uid,
    siteId,
    siteTitle: "Nova Coffee Roasters",
    deployedUrl: "https://nova-coffee.fluxsites.app",
    hostingTier: "pro",
    paymentOrderId: "gpay_ord_001",
    amountUsdCents: 999,
    status: "active",
    createdAt: pastTimestamp(),
  };
}

function validSubscriptionPayload(uid) {
  const ts = pastTimestamp();
  return {
    userId: uid,
    planId: "pro_monthly",
    planName: "Flux Pro Hosting",
    priceUsdCents: 999,
    currencyCode: "USD",
    paymentNetwork: "Google Pay • VISA",
    paymentTokenSummary: "tok_test_gpay_4242",
    status: "active",
    customDomainsLimit: 5,
    bandwidthGb: 500,
    createdAt: ts,
    updatedAt: ts,
  };
}

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

test("Unauthenticated user cannot read or write sites", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("sites").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("sites").doc("site1").set(validSitePayload(ALICE_UID))
  );
});

test("Owner can create, list, update, and delete own site", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const siteRef = aliceDb.collection("users").doc(ALICE_UID).collection("sites").doc("site_1");

  await assertSucceeds(siteRef.set(validSitePayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("sites").get());
  await assertSucceeds(
    siteRef.update({
      deployStatus: "deployed",
      hostingTier: "pro",
      deployedUrl: "https://nova-coffee.fluxsites.app",
      deployCount: 1,
      updatedAt: pastTimestamp(),
    })
  );
  await assertSucceeds(siteRef.delete());
});

test("Cross-user access to sites, deployments, and subscriptions is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sites").doc("site_1").set(validSitePayload(ALICE_UID))
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("sites").doc("site_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("sites").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("sites").doc("site_2").set(validSitePayload(BOB_UID))
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("subscriptions").get());
});

test("Shadow update with ghost field or invalid subdomain is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const badGhost = { ...validSitePayload(ALICE_UID), isAdmin: true };
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("sites").doc("site_ghost").set(badGhost)
  );

  const badSubdomain = { ...validSitePayload(ALICE_UID), subdomain: "INVALID SPACE!" };
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("sites").doc("site_bad_sub").set(badSubdomain)
  );
});

test("Deployment terminal state lock prevents modifying rolled_back deployment", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const depRef = aliceDb.collection("users").doc(ALICE_UID).collection("deployments").doc("dep_1");

  await assertSucceeds(depRef.set(validDeploymentPayload(ALICE_UID, "site_1")));
  await assertSucceeds(depRef.update({ status: "rolled_back" }));
  await assertFails(depRef.update({ status: "active" }));
});

test("Subscription creation and terminal cancellation work with strict schema", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const subRef = aliceDb.collection("users").doc(ALICE_UID).collection("subscriptions").doc("sub_1");

  await assertSucceeds(subRef.set(validSubscriptionPayload(ALICE_UID)));
  await assertSucceeds(subRef.update({ status: "cancelled", updatedAt: pastTimestamp() }));
  await assertFails(subRef.update({ status: "active", updatedAt: pastTimestamp() }));
});
