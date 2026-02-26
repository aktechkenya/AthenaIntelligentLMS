import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Building2, CheckCircle, Clock, XCircle } from "lucide-react";

const businesses = [
  { id: "BIZ-001", name: "Savanna Traders Ltd", regNo: "PVT-2024-88412", sector: "Retail", status: "Verified", submittedDate: "2026-01-15", verifiedDate: "2026-01-20" },
  { id: "BIZ-002", name: "Rift Valley Agri Co.", regNo: "PVT-2023-55109", sector: "Agriculture", status: "Pending", submittedDate: "2026-02-22", verifiedDate: "—" },
  { id: "BIZ-003", name: "Nairobi Tech Hub", regNo: "PVT-2025-00231", sector: "Technology", status: "Verified", submittedDate: "2025-12-10", verifiedDate: "2025-12-18" },
  { id: "BIZ-004", name: "Mombasa Shipping Co.", regNo: "PVT-2024-71003", sector: "Logistics", status: "Rejected", submittedDate: "2026-02-05", verifiedDate: "—" },
  { id: "BIZ-005", name: "Lake Region Dairy", regNo: "PVT-2025-12890", sector: "Agriculture", status: "Verified", submittedDate: "2026-01-28", verifiedDate: "2026-02-02" },
  { id: "BIZ-006", name: "Kilifi Hospitality Group", regNo: "PVT-2025-33412", sector: "Hospitality", status: "Pending", submittedDate: "2026-02-23", verifiedDate: "—" },
];

const statusStyle: Record<string, string> = {
  Verified: "bg-success/10 text-success border-success/20",
  Pending: "bg-warning/10 text-warning border-warning/20",
  Rejected: "bg-destructive/10 text-destructive border-destructive/20",
};

const statusIcon: Record<string, React.ReactNode> = {
  Verified: <CheckCircle className="h-3.5 w-3.5" />,
  Pending: <Clock className="h-3.5 w-3.5" />,
  Rejected: <XCircle className="h-3.5 w-3.5" />,
};

const KYBPage = () => {
  return (
    <DashboardLayout
      title="Business (KYB)"
      subtitle="Know Your Business verification"
      breadcrumbs={[{ label: "Home", href: "/" }, { label: "Customers" }, { label: "Business (KYB)" }]}
    >
      <div className="space-y-6 animate-fade-in">
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground font-sans">Total Businesses</span>
                <Building2 className="h-4 w-4 text-muted-foreground" />
              </div>
              <p className="text-2xl font-heading">{businesses.length}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground font-sans">Verified</span>
                <CheckCircle className="h-4 w-4 text-success" />
              </div>
              <p className="text-2xl font-heading">{businesses.filter((b) => b.status === "Verified").length}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground font-sans">Pending Review</span>
                <Clock className="h-4 w-4 text-warning" />
              </div>
              <p className="text-2xl font-heading">{businesses.filter((b) => b.status === "Pending").length}</p>
            </CardContent>
          </Card>
        </div>

        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-medium">Business Verification Registry</CardTitle>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="text-xs">ID</TableHead>
                  <TableHead className="text-xs">Business Name</TableHead>
                  <TableHead className="text-xs">Reg. Number</TableHead>
                  <TableHead className="text-xs">Sector</TableHead>
                  <TableHead className="text-xs">Submitted</TableHead>
                  <TableHead className="text-xs">Verified</TableHead>
                  <TableHead className="text-xs">Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {businesses.map((b) => (
                  <TableRow key={b.id} className="table-row-hover">
                    <TableCell className="text-xs font-mono">{b.id}</TableCell>
                    <TableCell className="text-sm font-medium">{b.name}</TableCell>
                    <TableCell className="text-xs font-mono text-muted-foreground">{b.regNo}</TableCell>
                    <TableCell className="text-xs">{b.sector}</TableCell>
                    <TableCell className="text-xs">{b.submittedDate}</TableCell>
                    <TableCell className="text-xs text-muted-foreground">{b.verifiedDate}</TableCell>
                    <TableCell>
                      <Badge variant="outline" className={`text-[10px] gap-1 ${statusStyle[b.status] || ""}`}>
                        {statusIcon[b.status]} {b.status}
                      </Badge>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </div>
    </DashboardLayout>
  );
};

export default KYBPage;
