import { useQuery } from "@tanstack/react-query";
import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Loader2, Building2 } from "lucide-react";
import { orgService } from "@/services/orgService";

const BranchDirectoryPage = () => {
  const { data: settings, isLoading, isError } = useQuery({
    queryKey: ["org", "settings"],
    queryFn: () => orgService.getSettings(),
  });

  return (
    <DashboardLayout
      title="Branch Directory"
      subtitle="All registered branches for this organization"
    >
      {isLoading && (
        <div className="flex items-center justify-center h-64 text-muted-foreground">
          <Loader2 className="h-6 w-6 animate-spin mr-2" />
          <span>Loading branch directory...</span>
        </div>
      )}

      {isError && (
        <div className="flex items-center justify-center h-64 text-destructive">
          <p>Failed to load branch directory. Please try again.</p>
        </div>
      )}

      {settings && (
        <Card>
          <CardHeader className="flex flex-row items-center gap-2">
            <Building2 className="h-5 w-5 text-muted-foreground" />
            <CardTitle className="text-base">Branches</CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Branch Name</TableHead>
                  <TableHead>Country</TableHead>
                  <TableHead>Currency</TableHead>
                  <TableHead>Address</TableHead>
                  <TableHead>Contact</TableHead>
                  <TableHead>Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableRow>
                  <TableCell className="font-medium">
                    Head Office
                    <p className="text-xs text-muted-foreground font-normal">
                      {settings.orgName ?? "Athena Financial Services Ltd"}
                    </p>
                  </TableCell>
                  <TableCell>{settings.countryCode ?? "KEN"}</TableCell>
                  <TableCell>{settings.currency ?? "KES"}</TableCell>
                  <TableCell className="text-muted-foreground">—</TableCell>
                  <TableCell className="text-muted-foreground">—</TableCell>
                  <TableCell>
                    <Badge variant="default">Active</Badge>
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}
    </DashboardLayout>
  );
};

export default BranchDirectoryPage;
