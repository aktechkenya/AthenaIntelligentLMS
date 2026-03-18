#!/usr/bin/env python3
"""
AthenaLMS — Populate 300 customers with full data across all services.
Fills every portal tab: customers, accounts, loans, payments, collections,
compliance, scoring, overdraft, media, notifications, GL accounting.

Usage:
    source tests/.venv/bin/activate
    python scripts/populate-300-customers.py
    python scripts/populate-300-customers.py --count 100  # fewer customers
"""
import argparse
import random
import string
import sys
import time
import uuid
import requests

# ─── Config ───────────────────────────────────────────────────────────────────
BASE = "http://localhost"
ACCT   = f"{BASE}:28086"
PROD   = f"{BASE}:28087"
ORIG   = f"{BASE}:28088"
LOAN   = f"{BASE}:28089"
PAY    = f"{BASE}:28090"
ACCTG  = f"{BASE}:28091"
FLOAT  = f"{BASE}:28092"
COLL   = f"{BASE}:28093"
COMP   = f"{BASE}:28094"
SCORE  = f"{BASE}:28096"
OD     = f"{BASE}:28097"
MEDIA  = f"{BASE}:28098"
NOTIF  = f"{BASE}:28099"
TIMEOUT = 15

FIRST_NAMES = [
    "James","Mary","John","Patricia","Robert","Jennifer","Michael","Linda","David","Elizabeth",
    "William","Barbara","Richard","Susan","Joseph","Jessica","Thomas","Sarah","Charles","Karen",
    "Christopher","Lisa","Daniel","Nancy","Matthew","Betty","Anthony","Margaret","Mark","Sandra",
    "Donald","Ashley","Steven","Kimberly","Paul","Emily","Andrew","Donna","Joshua","Michelle",
    "Kenneth","Dorothy","Kevin","Carol","Brian","Amanda","George","Melissa","Timothy","Deborah",
    "Ronald","Stephanie","Edward","Rebecca","Jason","Sharon","Jeffrey","Laura","Ryan","Cynthia",
    "Jacob","Kathleen","Gary","Amy","Nicholas","Angela","Eric","Shirley","Jonathan","Anna",
    "Stephen","Brenda","Larry","Pamela","Justin","Emma","Scott","Nicole","Brandon","Helen",
    "Benjamin","Samantha","Samuel","Katherine","Raymond","Christine","Gregory","Debra","Frank","Rachel",
    "Alexander","Carolyn","Patrick","Janet","Peter","Catherine","Tyler","Maria","Dennis","Heather",
]
LAST_NAMES = [
    "Ochieng","Wanjiku","Kimani","Mwangi","Njoroge","Kamau","Mutua","Otieno","Akinyi","Wambui",
    "Kipchoge","Chebet","Korir","Langat","Rotich","Kibet","Cherono","Kiptoo","Jepkosgei","Tanui",
    "Nyong'o","Odinga","Ruto","Kenyatta","Moi","Kibaki","Amina","Hassan","Omar","Abdullahi",
    "Omondi","Adhiambo","Onyango","Awino","Akoth","Ouma","Owino","Auma","Okello","Anyango",
    "Muthoni","Nyambura","Waithera","Wangari","Njeri","Mumbi","Wangui","Nyokabi","Wairimbi","Gathoni",
    "Kariuki","Githinji","Ndungu","Kihara","Thuku","Gachanja","Njenga","Muriithi","Nderitu","Waweru",
    "Barasa","Wekesa","Simiyu","Masinde","Wafula","Namukolo","Namusonge","Wanyonyi","Khaemba","Makokha",
    "Said","Bakari","Mwinyi","Salim","Juma","Hamisi","Khamis","Rashid","Mwinyikai","Athman",
    "Smith","Johnson","Williams","Brown","Jones","Garcia","Miller","Davis","Rodriguez","Martinez",
    "Hernandez","Lopez","Gonzalez","Wilson","Anderson","Thomas","Taylor","Moore","Jackson","Martin",
]
COUNTIES = ["Nairobi","Mombasa","Kisumu","Nakuru","Eldoret","Thika","Nyeri","Machakos","Malindi","Garissa",
            "Nanyuki","Lamu","Kitale","Bungoma","Kakamega","Migori","Kericho","Embu","Meru","Isiolo"]
CUSTOMER_TYPES = ["INDIVIDUAL", "INDIVIDUAL", "INDIVIDUAL", "BUSINESS"]
ALERT_TYPES = ["LARGE_TRANSACTION","UNUSUAL_PATTERN","RAPID_TRANSACTIONS","STRUCTURING","PEP_MATCH","HIGH_RISK_CUSTOMER","GEOGRAPHIC_RISK"]
ALERT_SEVERITY = ["LOW","MEDIUM","HIGH","CRITICAL"]
KYC_TIERS = ["BASIC","STANDARD","ENHANCED"]
ID_TYPES = ["NATIONAL_ID","PASSPORT","DRIVING_LICENSE"]

# ─── Stats ────────────────────────────────────────────────────────────────────
stats = {
    "customers": 0, "accounts": 0, "loans_applied": 0, "loans_active": 0,
    "repayments": 0, "payments": 0, "wallets": 0, "overdrafts": 0,
    "kyc": 0, "alerts": 0, "media": 0, "errors": [],
}

def login():
    r = requests.post(f"{ACCT}/api/auth/login",
                      json={"username": "admin", "password": "admin123"}, timeout=TIMEOUT)
    assert r.status_code == 200, f"Login failed: {r.status_code}"
    return {"Authorization": f"Bearer {r.json()['token']}", "Content-Type": "application/json"}

def uid(prefix="POP"):
    return f"{prefix}-{uuid.uuid4().hex[:8].upper()}"

def get_products(headers):
    r = requests.get(f"{PROD}/api/v1/products?size=100", headers=headers, timeout=TIMEOUT)
    items = r.json().get("content", r.json()) if isinstance(r.json(), dict) else r.json()
    return [p for p in items if p.get("status") == "ACTIVE"]

def progress(i, total, label=""):
    pct = int((i / total) * 100)
    bar = "█" * (pct // 2) + "░" * (50 - pct // 2)
    sys.stdout.write(f"\r  [{bar}] {pct:3d}% ({i}/{total}) {label:<40}")
    sys.stdout.flush()

def create_customer(i, headers):
    cid = f"POP-{i:04d}"
    fn = random.choice(FIRST_NAMES)
    ln = random.choice(LAST_NAMES)
    ctype = random.choice(CUSTOMER_TYPES)
    payload = {
        "customerId": cid,
        "firstName": fn,
        "lastName": ln,
        "email": f"{fn.lower()}.{ln.lower()}.{i}@athena.co.ke",
        "phone": f"+2547{random.randint(10000000, 99999999)}",
        "customerType": ctype,
        "status": "ACTIVE",
        "county": random.choice(COUNTIES),
    }
    r = requests.post(f"{ACCT}/api/v1/customers", json=payload, headers=headers, timeout=TIMEOUT)
    if r.status_code == 201:
        stats["customers"] += 1
        return r.json(), cid, fn, ln
    return None, cid, fn, ln

def create_account(cid, name, acct_type, headers):
    r = requests.post(f"{ACCT}/api/v1/accounts",
                      json={"customerId": cid, "accountType": acct_type, "currency": "KES", "name": name},
                      headers=headers, timeout=TIMEOUT)
    if r.status_code == 201:
        stats["accounts"] += 1
        return r.json()
    return None

def credit_account(acct_id, amount, headers):
    requests.post(f"{ACCT}/api/v1/accounts/{acct_id}/credit",
                  json={"amount": amount, "description": "Initial deposit", "reference": uid("DEP")},
                  headers=headers, timeout=TIMEOUT)

def create_kyc(cid, fn, ln, headers):
    payload = {
        "customerId": cid,
        "firstName": fn,
        "lastName": ln,
        "idType": random.choice(ID_TYPES),
        "idNumber": f"{random.randint(10000000, 99999999)}",
        "tier": random.choice(KYC_TIERS),
    }
    r = requests.post(f"{COMP}/api/v1/compliance/kyc", json=payload, headers=headers, timeout=TIMEOUT)
    if r.status_code in (200, 201):
        stats["kyc"] += 1
        # Randomly pass some KYC
        if random.random() < 0.8:
            requests.post(f"{COMP}/api/v1/compliance/kyc/{cid}/pass", headers=headers, timeout=TIMEOUT)

def create_alert(cid, headers):
    payload = {
        "customerId": cid,
        "alertType": random.choice(ALERT_TYPES),
        "severity": random.choice(ALERT_SEVERITY),
        "description": f"Auto-generated alert for monitoring review",
        "subjectType": "CUSTOMER",
        "subjectId": cid,
    }
    r = requests.post(f"{COMP}/api/v1/compliance/alerts", json=payload, headers=headers, timeout=TIMEOUT)
    if r.status_code == 201:
        stats["alerts"] += 1

def create_wallet(cid, headers):
    r = requests.post(f"{OD}/api/v1/wallets",
                      json={"customerId": cid, "currency": "KES"},
                      headers=headers, timeout=TIMEOUT)
    if r.status_code in (200, 201):
        stats["wallets"] += 1
        wallet = r.json()
        wid = wallet["id"]
        # Deposit random amount
        dep_amt = random.randint(5000, 100000)
        requests.post(f"{OD}/api/v1/wallets/{wid}/deposit",
                      json={"amount": dep_amt, "reference": uid("DEP")},
                      headers=headers, timeout=TIMEOUT)
        # Some withdrawals
        if random.random() < 0.6:
            wd_amt = random.randint(1000, dep_amt // 2)
            requests.post(f"{OD}/api/v1/wallets/{wid}/withdraw",
                          json={"amount": wd_amt, "reference": uid("WDR")},
                          headers=headers, timeout=TIMEOUT)
        return wid
    return None

def apply_overdraft(wallet_id, headers):
    r = requests.post(f"{OD}/api/v1/wallets/{wallet_id}/overdraft/apply",
                      json={"requestedLimit": random.choice([5000, 10000, 25000, 50000])},
                      headers=headers, timeout=TIMEOUT)
    if r.status_code in (200, 201):
        stats["overdrafts"] += 1

def create_loan(cid, product, acct_id, headers):
    """Create loan application through full lifecycle."""
    ptype = product["productType"]
    min_amt = float(product.get("minAmount", 1000))
    max_amt = float(product.get("maxAmount", 100000))
    min_tenor = int(product.get("minTenorMonths", 1))
    max_tenor = int(product.get("maxTenorMonths", 12))

    amount = round(random.uniform(max(min_amt, 1000), min(max_amt, 100000)), 0)
    tenor = random.randint(min_tenor, min(max_tenor, 12))

    payload = {
        "customerId": cid,
        "productId": product["id"],
        "requestedAmount": amount,
        "tenorMonths": tenor,
        "purpose": f"{ptype} loan for {cid}",
    }
    r = requests.post(f"{ORIG}/api/v1/loan-applications", json=payload, headers=headers, timeout=TIMEOUT)
    if r.status_code != 201:
        return None
    app_id = r.json()["id"]
    stats["loans_applied"] += 1

    # Submit
    r = requests.post(f"{ORIG}/api/v1/loan-applications/{app_id}/submit", headers=headers, timeout=TIMEOUT)
    if r.status_code != 200:
        return None

    # Review
    r = requests.post(f"{ORIG}/api/v1/loan-applications/{app_id}/review/start", headers=headers, timeout=TIMEOUT)
    if r.status_code != 200:
        return None

    # Randomly reject ~10%
    if random.random() < 0.1:
        requests.post(f"{ORIG}/api/v1/loan-applications/{app_id}/review/reject",
                      json={"reason": "Credit risk too high", "comments": "Auto-rejected by population script"},
                      headers=headers, timeout=TIMEOUT)
        return None

    # Approve
    r = requests.post(f"{ORIG}/api/v1/loan-applications/{app_id}/review/approve",
                      json={"approvedAmount": amount, "interestRate": random.uniform(10, 25),
                            "comments": "Auto-approved"},
                      headers=headers, timeout=TIMEOUT)
    if r.status_code != 200:
        return None

    # Disburse
    r = requests.post(f"{ORIG}/api/v1/loan-applications/{app_id}/disburse",
                      json={"disbursedAmount": amount, "disbursementAccount": str(acct_id),
                            "disbursementMethod": "BANK_TRANSFER"},
                      headers=headers, timeout=TIMEOUT)
    if r.status_code != 200:
        return None

    return {"app_id": app_id, "amount": amount, "cid": cid}

def wait_and_repay(cid, headers, repay_pct):
    """Find active loan for customer, make partial/full repayment."""
    r = requests.get(f"{LOAN}/api/v1/loans/customer/{cid}", headers=headers, timeout=TIMEOUT)
    if r.status_code != 200:
        return
    loans = r.json()
    items = loans.get("content", loans) if isinstance(loans, dict) else loans
    active = [l for l in items if l.get("status") == "ACTIVE"]
    if not active:
        return

    loan = active[0]
    loan_id = loan["id"]

    # Get schedule
    r = requests.get(f"{LOAN}/api/v1/loans/{loan_id}/schedule", headers=headers, timeout=TIMEOUT)
    if r.status_code != 200:
        return
    schedule = r.json()
    installments = schedule if isinstance(schedule, list) else schedule.get("installments", schedule.get("content", []))
    if not installments:
        return

    # Pay first N installments based on repay_pct
    num_to_pay = max(1, int(len(installments) * repay_pct))
    for inst in installments[:num_to_pay]:
        total_due = float(inst.get("totalDue", inst.get("totalAmount", inst.get("installmentAmount", 0))))
        if total_due <= 0:
            continue
        r = requests.post(f"{LOAN}/api/v1/loans/{loan_id}/repayments",
                          json={"amount": total_due, "paymentMethod": random.choice(["CASH", "BANK_TRANSFER", "MPESA"]),
                                "reference": uid("RPMT")},
                          headers=headers, timeout=TIMEOUT)
        if r.status_code == 201:
            stats["repayments"] += 1

def create_payment(cid, headers):
    """Create a standalone payment record."""
    payload = {
        "customerId": cid,
        "amount": random.randint(500, 50000),
        "currency": "KES",
        "paymentType": random.choice(["LOAN_REPAYMENT", "FEE", "LOAN_DISBURSEMENT"]),
        "paymentMethod": random.choice(["CASH", "BANK_TRANSFER", "MPESA", "CARD"]),
        "paymentChannel": random.choice(["CASH", "BANK_TRANSFER", "MPESA", "CARD"]),
        "reference": uid("PAY"),
        "description": f"Payment for {cid}",
    }
    r = requests.post(f"{PAY}/api/v1/payments", json=payload, headers=headers, timeout=TIMEOUT)
    if r.status_code == 201:
        stats["payments"] += 1

def upload_media(cid, headers):
    """Upload a small test document."""
    h = {k: v for k, v in headers.items() if k != "Content-Type"}
    doc_content = f"Customer document for {cid}\nGenerated: {time.strftime('%Y-%m-%d %H:%M')}\n"
    files = {"file": (f"{cid}_id.txt", doc_content.encode(), "text/plain")}
    r = requests.post(f"{MEDIA}/api/v1/media/upload/{cid}", files=files, headers=h, timeout=TIMEOUT)
    if r.status_code in (200, 201):
        stats["media"] += 1


def main():
    parser = argparse.ArgumentParser(description="Populate AthenaLMS with test data")
    parser.add_argument("--count", type=int, default=300, help="Number of customers (default: 300)")
    args = parser.parse_args()
    COUNT = args.count

    print("=" * 60)
    print(f"  AthenaLMS Data Population — {COUNT} Customers")
    print(f"  {time.strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 60)
    print()

    # Login
    print("  Logging in...")
    headers = login()
    print("  Logged in as admin ✓")

    # Get products
    products = get_products(headers)
    print(f"  Found {len(products)} active products:")
    for p in products:
        print(f"    - {p['name']} ({p['productType']}) [{p['id'][:8]}...]")
    print()

    # ─── Phase 1: Create customers + accounts + KYC ───────────────────────────
    print("Phase 1: Creating customers, accounts, KYC...")
    customer_data = []
    for i in range(1, COUNT + 1):
        progress(i, COUNT, "customers + accounts + KYC")
        cust, cid, fn, ln = create_customer(i, headers)
        if not cust:
            continue

        # Create SAVINGS account + seed
        savings = create_account(cid, f"{fn} Savings", "SAVINGS", headers)
        if savings:
            seed_amount = random.randint(20000, 500000)
            credit_account(savings["id"], seed_amount, headers)

        # KYC
        create_kyc(cid, fn, ln, headers)

        customer_data.append({
            "cid": cid, "fn": fn, "ln": ln,
            "savings_id": savings["id"] if savings else None,
        })
    print()
    print(f"  ✓ {stats['customers']} customers, {stats['accounts']} accounts, {stats['kyc']} KYC records")
    print()

    # ─── Phase 2: Loan applications across all products ───────────────────────
    print("Phase 2: Creating loan applications (all product types)...")
    loan_customers = []
    for i, cd in enumerate(customer_data, 1):
        progress(i, len(customer_data), "loan applications")
        if not cd["savings_id"]:
            continue
        # Pick a random product
        product = random.choice(products)
        result = create_loan(cd["cid"], product, cd["savings_id"], headers)
        if result:
            loan_customers.append(cd)
    print()
    print(f"  ✓ {stats['loans_applied']} applications created")
    print()

    # ─── Phase 3: Wait for RabbitMQ activation ────────────────────────────────
    print("Phase 3: Waiting 15s for RabbitMQ loan activation...")
    for t in range(15, 0, -1):
        sys.stdout.write(f"\r  Waiting... {t}s ")
        sys.stdout.flush()
        time.sleep(1)
    print()

    # ─── Phase 4: Repayments (varied: 0-100% paid) ───────────────────────────
    print("Phase 4: Making repayments (varied percentages)...")
    for i, cd in enumerate(loan_customers, 1):
        progress(i, len(loan_customers), "repayments")
        # Varied repayment: 30% pay nothing, 30% pay 1 installment, 20% pay half, 20% pay all
        r = random.random()
        if r < 0.3:
            repay_pct = 0
        elif r < 0.6:
            repay_pct = 0.15  # ~1 installment
        elif r < 0.8:
            repay_pct = 0.5
        else:
            repay_pct = 1.0
        if repay_pct > 0:
            wait_and_repay(cd["cid"], headers, repay_pct)
    print()
    print(f"  ✓ {stats['repayments']} repayments made")
    print()

    # ─── Phase 5: Wallets + Overdrafts ────────────────────────────────────────
    print("Phase 5: Creating wallets + overdraft facilities...")
    for i, cd in enumerate(customer_data, 1):
        progress(i, len(customer_data), "wallets + overdrafts")
        # 60% get wallets
        if random.random() < 0.6:
            wid = create_wallet(cd["cid"], headers)
            # 30% of wallet holders apply for overdraft
            if wid and random.random() < 0.3:
                apply_overdraft(wid, headers)
    print()
    print(f"  ✓ {stats['wallets']} wallets, {stats['overdrafts']} overdraft applications")
    print()

    # ─── Phase 6: Compliance alerts ───────────────────────────────────────────
    print("Phase 6: Creating compliance alerts...")
    alert_count = min(50, COUNT // 6)  # ~50 alerts
    alert_customers = random.sample(customer_data, min(alert_count, len(customer_data)))
    for i, cd in enumerate(alert_customers, 1):
        progress(i, len(alert_customers), "compliance alerts")
        create_alert(cd["cid"], headers)
    print()
    print(f"  ✓ {stats['alerts']} alerts created")
    print()

    # ─── Phase 7: Payments ────────────────────────────────────────────────────
    print("Phase 7: Creating payment records...")
    pay_count = min(100, COUNT // 3)
    pay_customers = random.sample(customer_data, min(pay_count, len(customer_data)))
    for i, cd in enumerate(pay_customers, 1):
        progress(i, len(pay_customers), "payment records")
        create_payment(cd["cid"], headers)
    print()
    print(f"  ✓ {stats['payments']} payments created")
    print()

    # ─── Phase 8: Media uploads ───────────────────────────────────────────────
    print("Phase 8: Uploading documents...")
    media_count = min(80, COUNT // 4)
    media_customers = random.sample(customer_data, min(media_count, len(customer_data)))
    for i, cd in enumerate(media_customers, 1):
        progress(i, len(media_customers), "document uploads")
        upload_media(cd["cid"], headers)
    print()
    print(f"  ✓ {stats['media']} documents uploaded")
    print()

    # ─── Summary ──────────────────────────────────────────────────────────────
    print("=" * 60)
    print("  POPULATION COMPLETE")
    print("=" * 60)
    print(f"  Customers:    {stats['customers']}")
    print(f"  Accounts:     {stats['accounts']}")
    print(f"  KYC Records:  {stats['kyc']}")
    print(f"  Loans Applied:{stats['loans_applied']}")
    print(f"  Repayments:   {stats['repayments']}")
    print(f"  Payments:     {stats['payments']}")
    print(f"  Wallets:      {stats['wallets']}")
    print(f"  Overdrafts:   {stats['overdrafts']}")
    print(f"  Alerts:       {stats['alerts']}")
    print(f"  Documents:    {stats['media']}")
    print("=" * 60)
    if stats["errors"]:
        print(f"\n  Errors ({len(stats['errors'])}):")
        for e in stats["errors"][:10]:
            print(f"    - {e}")


if __name__ == "__main__":
    main()
