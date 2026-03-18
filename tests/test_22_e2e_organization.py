"""
E2E Test: Organization settings & cross-cutting concerns.
"""
import pytest
import requests
from conftest import url, TIMEOUT


@pytest.mark.e2e
class TestOrganizationSettings:

    def test_get_org_settings(self, admin_headers):
        r = requests.get(url("account", "/api/v1/organization/settings"),
                         headers=admin_headers, timeout=TIMEOUT)
        # Go service may return 200 (settings exist) or 404 (no settings yet)
        assert r.status_code in (200, 404), f"Org settings returned {r.status_code}"


@pytest.mark.e2e
@pytest.mark.skip(reason="Swagger/OpenAPI docs are Spring Boot specific — not implemented in Go services")
class TestSwaggerDocs:
    """Verify Swagger/OpenAPI docs are accessible for each service.
    Skipped for Go services which don't embed Swagger UI."""

    SERVICES_WITH_SWAGGER = [
        ("account",          8086),
        ("product",          8087),
        ("loan_origination", 8088),
        ("loan_management",  8089),
        ("payment",          8090),
        ("accounting",       8091),
        ("float",            8092),
        ("collections",      8093),
        ("compliance",       8094),
        ("reporting",        8095),
        ("scoring",          8096),
        ("overdraft",        8097),
        ("media",            8098),
        ("notification",     8099),
    ]

    @pytest.mark.parametrize("name,port", SERVICES_WITH_SWAGGER,
                             ids=[s[0] for s in SERVICES_WITH_SWAGGER])
    def test_swagger_ui(self, name, port):
        from conftest import SERVICES
        r = requests.get(f"{SERVICES[name]}/swagger-ui/index.html", timeout=TIMEOUT)
        assert r.status_code == 200, f"{name} Swagger UI returned {r.status_code}"

    @pytest.mark.parametrize("name,port", SERVICES_WITH_SWAGGER,
                             ids=[s[0] for s in SERVICES_WITH_SWAGGER])
    def test_api_docs(self, name, port):
        from conftest import SERVICES
        r = requests.get(f"{SERVICES[name]}/api-docs", timeout=TIMEOUT)
        assert r.status_code == 200, f"{name} API docs returned {r.status_code}"
