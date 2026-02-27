import { apiGet, apiPost, apiPut, PageResponse } from "@/lib/api";

const BASE = "/proxy/notifications/api/v1/notifications";

export interface NotificationLog {
  id: number;
  serviceName: string;
  type: string;
  recipient: string;
  subject?: string;
  body?: string;
  status: "SENT" | "FAILED" | "SKIPPED";
  errorMessage?: string;
  sentAt: string;
}

export interface NotificationConfig {
  id?: number;
  type: "EMAIL" | "SMS";
  provider?: string;
  host?: string;
  port?: number;
  username?: string;
  password?: string;
  fromAddress?: string;
  apiKey?: string;
  apiSecret?: string;
  senderId?: string;
  enabled: boolean;
}

export const notificationService = {
  async getLogs(page = 0, size = 20): Promise<PageResponse<NotificationLog>> {
    const result = await apiGet<PageResponse<NotificationLog>>(
      `${BASE}/logs?page=${page}&size=${size}`
    );
    return result.data ?? { content: [], totalElements: 0, totalPages: 0, number: 0 };
  },

  async getConfig(type: "EMAIL" | "SMS"): Promise<NotificationConfig | null> {
    const result = await apiGet<NotificationConfig>(`${BASE}/config/${type}`);
    return result.data ?? null;
  },

  async updateConfig(config: NotificationConfig): Promise<NotificationConfig> {
    const result = await apiPost<NotificationConfig>(`${BASE}/config`, config);
    if (!result.data) throw new Error(result.error ?? "Failed to update config");
    return result.data;
  },

  async sendManual(req: {
    recipient: string;
    subject: string;
    message: string;
    type?: "EMAIL" | "SMS";
  }): Promise<string> {
    const result = await apiPost<string>(`${BASE}/send`, {
      recipient: req.recipient,
      subject: req.subject,
      message: req.message,
      type: req.type ?? "EMAIL",
    });
    return result.data ?? "Sent";
  },
};
