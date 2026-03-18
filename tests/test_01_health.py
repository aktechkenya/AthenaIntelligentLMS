"""Health checks for all 15 LMS services."""
import pytest
import requests
from conftest import SERVICES, TIMEOUT

HEALTH_SERVICES = [
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
    ("gateway",          8105),
]


@pytest.mark.health
@pytest.mark.smoke
class TestServiceHealth:

    @pytest.mark.parametrize("name,port", HEALTH_SERVICES, ids=[s[0] for s in HEALTH_SERVICES])
    def test_actuator_health(self, name, port):
        """Each service /actuator/health returns UP."""
        r = requests.get(f"{SERVICES[name]}/actuator/health", timeout=TIMEOUT)
        assert r.status_code == 200, f"{name} health returned {r.status_code}"
        body = r.json()
        assert body.get("status") == "UP", f"{name} status={body.get('status')}"

    @pytest.mark.parametrize("name,port", HEALTH_SERVICES, ids=[s[0] for s in HEALTH_SERVICES])
    def test_actuator_info(self, name, port):
        """Each service /actuator/info is reachable (may require auth).
        Go services return 404 (no /actuator/info endpoint) — this is acceptable."""
        r = requests.get(f"{SERVICES[name]}/actuator/info", timeout=TIMEOUT)
        assert r.status_code in (200, 401, 404), f"{name} info returned {r.status_code}"
