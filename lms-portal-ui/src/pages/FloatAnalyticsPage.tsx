import { DashboardLayout } from "@/components/DashboardLayout";

const FloatAnalyticsPage = () => (
  <DashboardLayout
    title="Float Analytics"
    subtitle="Float deployment and utilisation trends"
    breadcrumbs={[{ label: "Home", href: "/" }, { label: "Float & Wallet" }, { label: "Float Analytics" }]}
  >
    <div className="flex flex-col items-center justify-center h-64 text-muted-foreground">
      <p className="text-lg font-medium">No data available</p>
      <p className="text-sm">Backend integration for this module is pending.</p>
    </div>
  </DashboardLayout>
);

export default FloatAnalyticsPage;
