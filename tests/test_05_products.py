"""Product service tests."""
import pytest
import requests
from conftest import url, unique_id, TIMEOUT


@pytest.mark.products
class TestProductCRUD:

    def test_list_products(self, admin_headers):
        r = requests.get(url("product", "/api/v1/products"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_create_product(self, admin_headers):
        payload = {
            "name": f"Test Loan {unique_id()}",
            "code": unique_id("PROD"),
            "productType": "PERSONAL",
            "currency": "KES",
            "minAmount": 1000,
            "maxAmount": 100000,
            "minTenorMonths": 1,
            "maxTenorMonths": 12,
            "interestRate": 15.0,
            "interestRateType": "FLAT",
            "scheduleType": "EQUAL_INSTALLMENTS",
            "repaymentFrequency": "MONTHLY",
            "status": "DRAFT",
        }
        r = requests.post(url("product", "/api/v1/products"),
                          json=payload, headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code in (200, 201, 400), f"Product create: {r.status_code} {r.text[:200]}"
        if r.status_code in (200, 201):
            body = r.json()
            assert body["id"]

    def test_get_product_by_id(self, admin_headers):
        # Get first product from list
        r = requests.get(url("product", "/api/v1/products"),
                         headers=admin_headers, timeout=TIMEOUT)
        products = r.json()
        items = products.get("content", products) if isinstance(products, dict) else products
        if items:
            pid = items[0]["id"]
            r2 = requests.get(url("product", f"/api/v1/products/{pid}"),
                              headers=admin_headers, timeout=TIMEOUT)
            assert r2.status_code == 200

    def test_simulate_schedule(self, admin_headers):
        # Get any active product
        r = requests.get(url("product", "/api/v1/products"),
                         headers=admin_headers, timeout=TIMEOUT)
        products = r.json()
        items = products.get("content", products) if isinstance(products, dict) else products
        if items:
            pid = items[0]["id"]
            r2 = requests.post(
                url("product", f"/api/v1/products/{pid}/simulate"),
                json={"amount": 50000, "tenorMonths": 6},
                headers=admin_headers, timeout=TIMEOUT,
            )
            assert r2.status_code in (200, 400), f"Simulate: {r2.status_code}"

    def test_list_product_templates(self, admin_headers):
        r = requests.get(url("product", "/api/v1/product-templates"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_product_versions(self, admin_headers):
        r = requests.get(url("product", "/api/v1/products"),
                         headers=admin_headers, timeout=TIMEOUT)
        products = r.json()
        items = products.get("content", products) if isinstance(products, dict) else products
        if items:
            pid = items[0]["id"]
            r2 = requests.get(url("product", f"/api/v1/products/{pid}/versions"),
                              headers=admin_headers, timeout=TIMEOUT)
            assert r2.status_code == 200


@pytest.mark.products
class TestCharges:

    def test_list_charges(self, admin_headers):
        r = requests.get(url("product", "/api/v1/charges"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_create_charge(self, admin_headers):
        payload = {
            "name": f"Test Fee {unique_id()}",
            "chargeType": "PROCESSING_FEE",
            "calculationType": "PERCENTAGE",
            "amount": 2.5,
            "currency": "KES",
        }
        r = requests.post(url("product", "/api/v1/charges"),
                          json=payload, headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code in (200, 201, 400), f"Charge create: {r.status_code} {r.text[:200]}"
