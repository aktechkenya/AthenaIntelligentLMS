"""Media service tests — document management."""
import pytest
import requests
from conftest import url, TIMEOUT


@pytest.mark.media
class TestMediaService:

    def test_list_media(self, admin_headers):
        r = requests.get(url("media", "/api/v1/media"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_upload_document(self, admin_headers, test_customer):
        cid = test_customer["_customerId"]
        # Multipart upload with a small test file
        files = {"file": ("test.txt", b"Pytest test document content", "text/plain")}
        headers = {k: v for k, v in admin_headers.items() if k != "Content-Type"}
        r = requests.post(url("media", f"/api/v1/media/upload/{cid}"),
                          files=files, headers=headers, timeout=TIMEOUT)
        assert r.status_code in (200, 201)

    def test_get_media_by_customer(self, admin_headers, test_customer):
        cid = test_customer["_customerId"]
        r = requests.get(url("media", f"/api/v1/media/customer/{cid}"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_media_stats(self, admin_headers):
        r = requests.get(url("media", "/api/v1/media/stats"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_media_stats_all(self, admin_headers):
        r = requests.get(url("media", "/api/v1/media/stats/all"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
