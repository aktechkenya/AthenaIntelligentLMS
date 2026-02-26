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
import { Users, Info } from "lucide-react";

const ROLE_VARIANT: Record<string, "default" | "secondary" | "outline"> = {
  ADMIN: "default",
  MANAGER: "secondary",
  OFFICER: "outline",
  TELLER: "outline",
};

const SYSTEM_USERS = [
  {
    username: "admin",
    name: "Administrator",
    email: "admin@athena.lms",
    role: "ADMIN",
    status: "Active",
  },
  {
    username: "manager",
    name: "Loan Manager",
    email: "manager@athena.lms",
    role: "MANAGER",
    status: "Active",
  },
  {
    username: "officer",
    name: "Loan Officer",
    email: "officer@athena.lms",
    role: "OFFICER",
    status: "Active",
  },
  {
    username: "teller@athena.com",
    name: "Teller",
    email: "teller@athena.com",
    role: "TELLER",
    status: "Active",
  },
];

const UsersPage = () => (
  <DashboardLayout
    title="Users & Roles"
    subtitle="User accounts and role-based access control"
  >
    <div className="space-y-4">
      <Card>
        <CardHeader className="flex flex-row items-center gap-2">
          <Users className="h-5 w-5 text-muted-foreground" />
          <CardTitle className="text-base">System Users</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Email</TableHead>
                <TableHead>Role</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {SYSTEM_USERS.map((user) => (
                <TableRow key={user.username}>
                  <TableCell className="font-medium">{user.name}</TableCell>
                  <TableCell className="text-muted-foreground text-sm">{user.email}</TableCell>
                  <TableCell>
                    <Badge variant={ROLE_VARIANT[user.role] ?? "outline"}>
                      {user.role}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <Badge variant="default">{user.status}</Badge>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <div className="flex items-start gap-2 p-3 bg-blue-50 border border-blue-200 rounded-lg text-sm text-blue-800">
        <Info className="h-4 w-4 mt-0.5 shrink-0" />
        <span>
          These are system-seeded users. User management API (create, update, disable) is coming
          soon.
        </span>
      </div>
    </div>
  </DashboardLayout>
);

export default UsersPage;
