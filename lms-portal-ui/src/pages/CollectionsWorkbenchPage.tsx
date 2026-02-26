import { DashboardLayout } from "@/components/DashboardLayout";

const CollectionsWorkbenchPage = () => (
  <DashboardLayout
    title="Collections Workbench"
    subtitle="Delinquency management and borrower outreach"
    breadcrumbs={[{ label: "Home", href: "/" }, { label: "Collections" }, { label: "Workbench" }]}
  >
    <div className="flex flex-col items-center justify-center h-64 text-muted-foreground">
      <p className="text-lg font-medium">No data available</p>
      <p className="text-sm">Backend integration for this module is pending.</p>
    </div>
  </DashboardLayout>
);

export default CollectionsWorkbenchPage;
