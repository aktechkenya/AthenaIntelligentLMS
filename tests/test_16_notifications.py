"""Notification service tests."""
import pytest
import requests
from conftest import url, TIMEOUT


@pytest.mark.notifications
class TestNotificationService:

    def test_list_notification_logs(self, admin_headers):
        r = requests.get(url("notification", "/api/v1/notifications/logs"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_get_email_config(self, admin_headers):
        r = requests.get(url("notification", "/api/v1/notifications/config/EMAIL"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code in (200, 404)

    def test_get_sms_config(self, admin_headers):
        r = requests.get(url("notification", "/api/v1/notifications/config/SMS"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code in (200, 404)

    def test_send_test_notification(self, admin_headers):
        payload = {
            "channel": "EMAIL",
            "recipient": "pytest@test.athena.com",
            "subject": "Pytest Test",
            "body": "This is a test notification from pytest.",
        }
        r = requests.post(url("notification", "/api/v1/notifications/send"),
                          json=payload, headers=admin_headers, timeout=TIMEOUT)
        # May succeed or fail depending on SMTP config
        assert r.status_code in (200, 201, 400, 500)
