import { useState } from "react";
import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table";
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select";
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Settings2, ShieldCheck, Zap } from "lucide-react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { fraudService, type FraudRule, type AlertSeverity } from "@/services/fraudService";
import { toast } from "sonner";

const severityColor: Record<AlertSeverity, string> = {
  CRITICAL: "bg-red-500/15 text-red-600 border-red-500/30",
  HIGH: "bg-destructive/15 text-destructive border-destructive/30",
  MEDIUM: "bg-warning/15 text-warning border-warning/30",
  LOW: "bg-info/15 text-info border-info/30",
};

const categoryColor: Record<string, string> = {
  TRANSACTION: "bg-blue-500/10 text-blue-600 border-blue-500/20",
  AML: "bg-red-500/10 text-red-600 border-red-500/20",
  VELOCITY: "bg-orange-500/10 text-orange-600 border-orange-500/20",
  APPLICATION: "bg-purple-500/10 text-purple-600 border-purple-500/20",
  COMPLIANCE: "bg-pink-500/10 text-pink-600 border-pink-500/20",
  ACCOUNT: "bg-teal-500/10 text-teal-600 border-teal-500/20",
  OVERDRAFT: "bg-cyan-500/10 text-cyan-600 border-cyan-500/20",
  COLLECTIONS: "bg-amber-500/10 text-amber-600 border-amber-500/20",
  INTERNAL: "bg-gray-500/10 text-gray-600 border-gray-500/20",
};

const FraudRulesPage = () => {
  const queryClient = useQueryClient();
  const [editRule, setEditRule] = useState<FraudRule | null>(null);
  const [editSeverity, setEditSeverity] = useState("");
  const [editParams, setEditParams] = useState("");

  const { data: rules, isLoading } = useQuery({
    queryKey: ["fraud", "rules"],
    queryFn: () => fraudService.listRules(),
    staleTime: 60_000,
    retry: false,
  });

  const toggleMutation = useMutation({
    mutationFn: (params: { id: string; enabled: boolean }) =>
      fraudService.updateRule(params.id, { enabled: params.enabled }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["fraud", "rules"] });
      toast.success("Rule updated");
    },
    onError: (err: Error) => toast.error(err.message),
  });

  const updateMutation = useMutation({
    mutationFn: () => {
      if (!editRule) throw new Error("No rule selected");
      let params: Record<string, unknown> | undefined;
      try {
        params = editParams ? JSON.parse(editParams) : undefined;
      } catch {
        throw new Error("Invalid JSON parameters");
      }
      return fraudService.updateRule(editRule.id, {
        severity: editSeverity || undefined,
        parameters: params,
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["fraud", "rules"] });
      setEditRule(null);
      toast.success("Rule configuration saved");
    },
    onError: (err: Error) => toast.error(err.message),
  });

  const openEditDialog = (rule: FraudRule) => {
    setEditRule(rule);
    setEditSeverity(rule.severity);
    setEditParams(JSON.stringify(rule.parameters ?? {}, null, 2));
  };

  return (
    <DashboardLayout
      title="Fraud Detection Rules"
      subtitle="Configure and manage fraud detection rules and thresholds"
      breadcrumbs={[{ label: "Home", href: "/" }, { label: "Compliance" }, { label: "Rules" }]}
    >
      <div className="space-y-6 animate-fade-in">
        <div className="flex items-center gap-3">
          <Badge className="bg-success/10 text-success border-success/20 gap-1.5 px-3 py-1">
            <ShieldCheck className="h-3 w-3" />
            {rules?.filter((r) => r.enabled).length ?? 0} Active Rules
          </Badge>
          <span className="text-xs text-muted-foreground">
            {rules?.length ?? 0} total rules across {new Set(rules?.map((r) => r.category)).size ?? 0} categories
          </span>
        </div>

        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-medium flex items-center gap-2">
              <Settings2 className="h-4 w-4" />
              Rule Configuration
            </CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            {isLoading ? (
              <div className="p-4 space-y-2">{Array.from({ length: 8 }).map((_, i) => <Skeleton key={i} className="h-10 w-full" />)}</div>
            ) : (
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="text-xs w-[60px]">Active</TableHead>
                      <TableHead className="text-xs">Code</TableHead>
                      <TableHead className="text-xs">Name</TableHead>
                      <TableHead className="text-xs">Category</TableHead>
                      <TableHead className="text-xs">Severity</TableHead>
                      <TableHead className="text-xs">Events</TableHead>
                      <TableHead className="text-xs max-w-[200px]">Description</TableHead>
                      <TableHead className="text-xs text-right">Config</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {(rules ?? []).map((rule) => (
                      <TableRow key={rule.id} className="table-row-hover">
                        <TableCell>
                          <Switch
                            checked={rule.enabled}
                            onCheckedChange={(checked) =>
                              toggleMutation.mutate({ id: rule.id, enabled: checked })
                            }
                          />
                        </TableCell>
                        <TableCell className="text-xs font-mono font-medium">{rule.ruleCode}</TableCell>
                        <TableCell className="text-xs">{rule.ruleName}</TableCell>
                        <TableCell>
                          <Badge variant="outline" className={`text-[10px] ${categoryColor[rule.category] ?? ""}`}>
                            {rule.category}
                          </Badge>
                        </TableCell>
                        <TableCell>
                          <Badge variant="outline" className={`text-[10px] ${severityColor[rule.severity]}`}>
                            {rule.severity}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-[10px] text-muted-foreground max-w-[180px] truncate font-mono">
                          {rule.eventTypes?.split(",").length} events
                        </TableCell>
                        <TableCell className="text-xs text-muted-foreground max-w-[200px] truncate" title={rule.description}>
                          {rule.description ?? "—"}
                        </TableCell>
                        <TableCell className="text-right">
                          <Button variant="ghost" size="sm" className="h-7 px-2 text-xs gap-1"
                            onClick={() => openEditDialog(rule)}>
                            <Zap className="h-3 w-3" />
                            Edit
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Edit Rule Dialog */}
      <Dialog open={!!editRule} onOpenChange={() => setEditRule(null)}>
        <DialogContent className="sm:max-w-lg">
          {editRule && (
            <>
              <DialogHeader>
                <DialogTitle className="text-sm flex items-center gap-2">
                  <span className="font-mono">{editRule.ruleCode}</span>
                  <Badge variant="outline" className={`text-[10px] ${categoryColor[editRule.category]}`}>
                    {editRule.category}
                  </Badge>
                </DialogTitle>
              </DialogHeader>
              <div className="space-y-4">
                <p className="text-xs text-muted-foreground">{editRule.description}</p>

                <div>
                  <Label className="text-xs">Severity</Label>
                  <Select value={editSeverity} onValueChange={setEditSeverity}>
                    <SelectTrigger className="mt-1 text-xs h-9"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="LOW">Low</SelectItem>
                      <SelectItem value="MEDIUM">Medium</SelectItem>
                      <SelectItem value="HIGH">High</SelectItem>
                      <SelectItem value="CRITICAL">Critical</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <div>
                  <Label className="text-xs">Parameters (JSON)</Label>
                  <Input
                    value={editParams}
                    onChange={(e) => setEditParams(e.target.value)}
                    className="mt-1 text-xs font-mono h-auto"
                    placeholder='{"threshold": 1000000}'
                  />
                  <p className="text-[10px] text-muted-foreground mt-1">
                    Events: {editRule.eventTypes?.split(",").join(", ")}
                  </p>
                </div>
              </div>
              <DialogFooter>
                <Button variant="outline" size="sm" onClick={() => setEditRule(null)}>Cancel</Button>
                <Button size="sm" disabled={updateMutation.isPending} onClick={() => updateMutation.mutate()}>
                  {updateMutation.isPending ? "Saving..." : "Save Changes"}
                </Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>
    </DashboardLayout>
  );
};

export default FraudRulesPage;
