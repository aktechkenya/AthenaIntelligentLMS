"""Customer CRUD tests (account-service)."""
import pytest
import requests
from conftest import url, unique_id, TIMEOUT


@pytest.mark.customers
class TestCustomerCRUD:

    def test_create_customer(self, admin_headers):
        cid = unique_id("CUST")
        payload = {
            "customerId": cid,
            "firstName": "Test",
            "lastName": "Customer",
            "email": f"{cid.lower()}@test.com",
            "phone": "+254711111111",
            "customerType": "INDIVIDUAL",
            "status": "ACTIVE",
        }
        r = requests.post(url("account", "/api/v1/customers"),
                          json=payload, headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 201
        body = r.json()
        assert body["customerId"] == cid
        assert body["firstName"] == "Test"
        assert body["id"]  # UUID assigned

    def test_list_customers(self, admin_headers):
        r = requests.get(url("account", "/api/v1/customers"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        body = r.json()
        assert "content" in body or isinstance(body, list)

    def test_get_customer_by_id(self, admin_headers, test_customer):
        cust_id = test_customer["id"]
        r = requests.get(url("account", f"/api/v1/customers/{cust_id}"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        assert r.json()["id"] == cust_id

    def test_get_customer_by_customer_id(self, admin_headers, test_customer):
        cid = test_customer["_customerId"]
        r = requests.get(url("account", f"/api/v1/customers/by-customer-id/{cid}"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        assert r.json()["customerId"] == cid

    def test_update_customer(self, admin_headers, test_customer):
        cust_id = test_customer["id"]
        r = requests.put(
            url("account", f"/api/v1/customers/{cust_id}"),
            json={**test_customer, "lastName": "Updated"},
            headers=admin_headers,
            timeout=TIMEOUT,
        )
        assert r.status_code == 200
        assert r.json()["lastName"] == "Updated"

    def test_search_customers(self, admin_headers):
        r = requests.get(url("account", "/api/v1/customers/search?q=Pytest"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_create_customer_missing_fields(self, admin_headers):
        r = requests.post(url("account", "/api/v1/customers"),
                          json={"firstName": "Incomplete"},
                          headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code in (400, 422, 500)

    def test_get_nonexistent_customer(self, admin_headers):
        r = requests.get(
            url("account", "/api/v1/customers/00000000-0000-0000-0000-000000000000"),
            headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code in (404, 500)
