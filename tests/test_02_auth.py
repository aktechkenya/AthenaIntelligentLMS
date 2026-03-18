"""Authentication & authorization tests."""
import pytest
import requests
from conftest import url, DEMO_USERS, TIMEOUT, SERVICE_KEY


@pytest.mark.auth
class TestAuthentication:

    def test_admin_login(self):
        r = requests.post(url("account", "/api/auth/login"),
                          json=DEMO_USERS["admin"], timeout=TIMEOUT)
        assert r.status_code == 200
        body = r.json()
        assert body["token"]
        assert body["role"] == "ADMIN"
        assert body["tenantId"]

    def test_manager_login(self):
        r = requests.post(url("account", "/api/auth/login"),
                          json=DEMO_USERS["manager"], timeout=TIMEOUT)
        assert r.status_code == 200
        assert r.json()["role"] == "MANAGER"

    def test_officer_login(self):
        r = requests.post(url("account", "/api/auth/login"),
                          json=DEMO_USERS["officer"], timeout=TIMEOUT)
        assert r.status_code == 200
        assert r.json()["role"] in ("OFFICER", "LOAN_OFFICER")

    def test_teller_login(self):
        r = requests.post(url("account", "/api/auth/login"),
                          json=DEMO_USERS["teller"], timeout=TIMEOUT)
        assert r.status_code == 200

    def test_invalid_credentials(self):
        r = requests.post(url("account", "/api/auth/login"),
                          json={"username": "bad", "password": "wrong"}, timeout=TIMEOUT)
        assert r.status_code in (401, 403)

    def test_missing_password(self):
        r = requests.post(url("account", "/api/auth/login"),
                          json={"username": "admin"}, timeout=TIMEOUT)
        assert r.status_code in (400, 401, 403)

    def test_me_endpoint(self, admin_headers):
        r = requests.get(url("account", "/api/auth/me"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        assert r.json()["username"] == "admin"

    def test_no_auth_rejected(self):
        r = requests.get(url("account", "/api/v1/accounts"), timeout=TIMEOUT)
        assert r.status_code in (401, 403)

    def test_bad_token_rejected(self):
        r = requests.get(url("account", "/api/v1/accounts"),
                         headers={"Authorization": "Bearer invalid.token.here"},
                         timeout=TIMEOUT)
        assert r.status_code in (401, 403)


@pytest.mark.auth
class TestServiceKeyAuth:

    @pytest.mark.xfail(reason="service-key auth only supported at gateway level")
    def test_service_key_accepted(self):
        headers = {
            "X-Service-Key": SERVICE_KEY,
            "X-Service-Tenant": "admin",
            "Content-Type": "application/json",
        }
        r = requests.get(url("account", "/api/v1/accounts"),
                         headers=headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_wrong_service_key_rejected(self):
        headers = {
            "X-Service-Key": "wrongkey",
            "X-Service-Tenant": "admin",
            "Content-Type": "application/json",
        }
        r = requests.get(url("account", "/api/v1/accounts"),
                         headers=headers, timeout=TIMEOUT)
        assert r.status_code in (401, 403)

    def test_no_key_no_jwt_rejected(self):
        r = requests.get(url("account", "/api/v1/accounts"),
                         headers={"Content-Type": "application/json"},
                         timeout=TIMEOUT)
        assert r.status_code in (401, 403)
