const BASE = "/proxy/fraud-ml";

export interface MLHealthResponse {
  status: string;
  service: string;
  models: {
    anomaly_detector: string;
    fraud_scorer: string;
  };
}

export interface TrainingStatus {
  anomaly: { status: string; last_result: Record<string, unknown> | null };
  lgbm: { status: string; last_result: Record<string, unknown> | null };
}

export interface TrainResponse {
  status: string;
  message: string;
  details: Record<string, unknown>;
}

export const fraudMLService = {
  async health(): Promise<MLHealthResponse> {
    const r = await fetch(`${BASE}/health`);
    if (!r.ok) throw new Error(`ML health check failed: ${r.status}`);
    return r.json();
  },

  async trainingStatus(): Promise<TrainingStatus> {
    const r = await fetch(`${BASE}/api/v1/train/status`);
    if (!r.ok) throw new Error(`Training status failed: ${r.status}`);
    return r.json();
  },

  async trainAnomaly(lookbackDays = 90, contamination = 0.05): Promise<TrainResponse> {
    const r = await fetch(`${BASE}/api/v1/train/anomaly`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ lookback_days: lookbackDays, contamination }),
    });
    if (!r.ok) throw new Error(`Anomaly training failed: ${r.status}`);
    return r.json();
  },

  async trainFraudScorer(lookbackDays = 90): Promise<TrainResponse> {
    const r = await fetch(`${BASE}/api/v1/train/fraud-scorer`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ lookback_days: lookbackDays }),
    });
    if (!r.ok) throw new Error(`Fraud scorer training failed: ${r.status}`);
    return r.json();
  },

  async reloadModels(): Promise<{ status: string }> {
    const r = await fetch(`${BASE}/api/v1/models/reload`, { method: "POST" });
    if (!r.ok) throw new Error(`Model reload failed: ${r.status}`);
    return r.json();
  },
};
