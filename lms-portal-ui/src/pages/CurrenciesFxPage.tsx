import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useToast } from "@/hooks/use-toast";
import { orgService } from "@/services/orgService";
import { Loader2, Info, Pencil, AlertTriangle, TrendingUp, Coins } from "lucide-react";

// ── currency data ──────────────────────────────────────────────────────────

interface CurrencyDetail {
  code: string;
  name: string;
  symbol: string;
  country: string;
  flag: string;
  decimalPlaces: number;
}

const CURRENCIES: Record<string, CurrencyDetail> = {
  KES: { code: "KES", name: "Kenyan Shilling", symbol: "KSh", country: "Kenya", flag: "\u{1F1F0}\u{1F1EA}", decimalPlaces: 2 },
  UGX: { code: "UGX", name: "Ugandan Shilling", symbol: "USh", country: "Uganda", flag: "\u{1F1FA}\u{1F1EC}", decimalPlaces: 0 },
  TZS: { code: "TZS", name: "Tanzanian Shilling", symbol: "TSh", country: "Tanzania", flag: "\u{1F1F9}\u{1F1FF}", decimalPlaces: 2 },
  GHS: { code: "GHS", name: "Ghanaian Cedi", symbol: "GH\u20B5", country: "Ghana", flag: "\u{1F1EC}\u{1F1ED}", decimalPlaces: 2 },
  NGN: { code: "NGN", name: "Nigerian Naira", symbol: "\u20A6", country: "Nigeria", flag: "\u{1F1F3}\u{1F1EC}", decimalPlaces: 2 },
  USD: { code: "USD", name: "United States Dollar", symbol: "$", country: "United States", flag: "\u{1F1FA}\u{1F1F8}", decimalPlaces: 2 },
  EUR: { code: "EUR", name: "Euro", symbol: "\u20AC", country: "Eurozone", flag: "\u{1F1EA}\u{1F1FA}", decimalPlaces: 2 },
  GBP: { code: "GBP", name: "British Pound", symbol: "\u00A3", country: "United Kingdom", flag: "\u{1F1EC}\u{1F1E7}", decimalPlaces: 2 },
};

// Static cross-rates (illustrative, based on approximate mid-market rates)
const FX_RATES: { from: string; to: string; rate: number; inverse: number }[] = [
  { from: "USD", to: "KES", rate: 153.50, inverse: 0.00651 },
  { from: "USD", to: "UGX", rate: 3785.00, inverse: 0.000264 },
  { from: "USD", to: "TZS", rate: 2510.00, inverse: 0.000398 },
  { from: "USD", to: "GHS", rate: 14.85, inverse: 0.0673 },
  { from: "USD", to: "NGN", rate: 1550.00, inverse: 0.000645 },
  { from: "EUR", to: "USD", rate: 1.0870, inverse: 0.920 },
  { from: "GBP", to: "USD", rate: 1.2650, inverse: 0.7905 },
  { from: "KES", to: "UGX", rate: 24.66, inverse: 0.04055 },
  { from: "KES", to: "TZS", rate: 16.35, inverse: 0.06116 },
  { from: "KES", to: "GHS", rate: 0.0968, inverse: 10.33 },
  { from: "KES", to: "NGN", rate: 10.10, inverse: 0.0990 },
];

// ── component ──────────────────────────────────────────────────────────────

const CurrenciesFxPage = () => {
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const [showChangeDialog, setShowChangeDialog] = useState(false);
  const [selectedCurrency, setSelectedCurrency] = useState("");

  const { data: settings, isLoading, isError } = useQuery({
    queryKey: ["org", "settings"],
    queryFn: () => orgService.getSettings(),
  });

  const baseCurrency = settings?.currency ?? "KES";
  const currencyInfo = CURRENCIES[baseCurrency] ?? CURRENCIES.KES;

  const updateMutation = useMutation({
    mutationFn: (currency: string) => orgService.updateSettings({ currency }),
    onSuccess: (_data, currency) => {
      queryClient.invalidateQueries({ queryKey: ["org", "settings"] });
      const info = CURRENCIES[currency];
      toast({
        title: "Base Currency Updated",
        description: `Base currency changed to ${info.name} (${info.code})`,
      });
      setShowChangeDialog(false);
    },
    onError: (err: Error) => {
      toast({ title: "Update Failed", description: err.message, variant: "destructive" });
    },
  });

  const openChangeDialog = () => {
    setSelectedCurrency(baseCurrency);
    setShowChangeDialog(true);
  };

  const handleSaveCurrency = () => {
    if (!selectedCurrency) return;
    updateMutation.mutate(selectedCurrency);
  };

  const selectedPreview = selectedCurrency ? CURRENCIES[selectedCurrency] : null;

  return (
    <DashboardLayout
      title="Currencies & FX"
      subtitle="Base currency and foreign exchange configuration"
    >
      {isLoading && (
        <div className="flex items-center justify-center h-64 text-muted-foreground">
          <Loader2 className="h-6 w-6 animate-spin mr-2" />
          <span>Loading currency settings...</span>
        </div>
      )}

      {isError && (
        <div className="flex items-center justify-center h-64 text-destructive">
          <p>Failed to load currency settings. Please try again.</p>
        </div>
      )}

      {settings && (
        <div className="space-y-6">
          {/* Base Currency Card */}
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle className="text-base">Base Currency</CardTitle>
                <Button variant="outline" size="sm" className="gap-2" onClick={openChangeDialog}>
                  <Pencil className="h-3.5 w-3.5" />
                  Change Base Currency
                </Button>
              </div>
            </CardHeader>
            <CardContent>
              <div className="flex items-center justify-between p-5 border rounded-lg bg-muted/30">
                <div className="flex items-center gap-4">
                  <div className="flex items-center justify-center w-14 h-14 rounded-full bg-primary/10 text-primary text-2xl font-bold">
                    {currencyInfo.symbol}
                  </div>
                  <div>
                    <p className="font-semibold text-lg">{currencyInfo.name}</p>
                    <p className="text-sm text-muted-foreground">
                      ISO 4217: <span className="font-mono font-medium">{currencyInfo.code}</span>
                      <span className="mx-2">&middot;</span>
                      {currencyInfo.flag} {currencyInfo.country}
                      <span className="mx-2">&middot;</span>
                      {currencyInfo.decimalPlaces} decimal places
                    </p>
                  </div>
                </div>
                <Badge variant="default" className="text-sm px-3 py-1">Active</Badge>
              </div>
            </CardContent>
          </Card>

          {/* Supported Currencies */}
          <Card>
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <Coins className="h-4 w-4 text-primary" />
                Supported Currencies
              </CardTitle>
            </CardHeader>
            <CardContent className="p-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Currency</TableHead>
                    <TableHead>ISO Code</TableHead>
                    <TableHead>Symbol</TableHead>
                    <TableHead>Country</TableHead>
                    <TableHead className="text-center">Decimals</TableHead>
                    <TableHead className="text-center">Status</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {Object.values(CURRENCIES).map((curr) => (
                    <TableRow key={curr.code} className={curr.code === baseCurrency ? "bg-primary/5" : ""}>
                      <TableCell className="font-medium">
                        <span className="mr-2">{curr.flag}</span>
                        {curr.name}
                      </TableCell>
                      <TableCell className="font-mono font-medium">{curr.code}</TableCell>
                      <TableCell className="font-mono text-lg">{curr.symbol}</TableCell>
                      <TableCell>{curr.country}</TableCell>
                      <TableCell className="text-center">{curr.decimalPlaces}</TableCell>
                      <TableCell className="text-center">
                        {curr.code === baseCurrency ? (
                          <Badge variant="default">Base</Badge>
                        ) : (
                          <Badge variant="outline">Available</Badge>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>

          {/* FX Rates Table */}
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle className="text-base flex items-center gap-2">
                  <TrendingUp className="h-4 w-4 text-primary" />
                  Indicative FX Cross-Rates
                </CardTitle>
                <Badge variant="secondary" className="text-xs">Static / Illustrative</Badge>
              </div>
            </CardHeader>
            <CardContent className="p-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>From</TableHead>
                    <TableHead>To</TableHead>
                    <TableHead className="text-right">Rate</TableHead>
                    <TableHead className="text-right">Inverse</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {FX_RATES.map((fx, idx) => (
                    <TableRow key={idx}>
                      <TableCell className="font-mono font-medium">
                        {CURRENCIES[fx.from]?.flag ?? ""} {fx.from}
                      </TableCell>
                      <TableCell className="font-mono font-medium">
                        {CURRENCIES[fx.to]?.flag ?? ""} {fx.to}
                      </TableCell>
                      <TableCell className="text-right tabular-nums font-medium">
                        {fx.rate.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 4 })}
                      </TableCell>
                      <TableCell className="text-right tabular-nums text-muted-foreground">
                        {fx.inverse.toLocaleString(undefined, { minimumFractionDigits: 4, maximumFractionDigits: 6 })}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>

          {/* Info note */}
          <div className="flex items-start gap-2 p-3 bg-blue-50 border border-blue-200 rounded-lg text-sm text-blue-800">
            <Info className="h-4 w-4 mt-0.5 shrink-0" />
            <span>
              FX rates shown are static and for illustrative purposes only.
              Live rates integration coming soon. All transactions are currently processed in the base currency ({baseCurrency}).
            </span>
          </div>
        </div>
      )}

      {/* Change Currency Dialog */}
      <Dialog open={showChangeDialog} onOpenChange={setShowChangeDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Change Base Currency</DialogTitle>
            <DialogDescription>
              Select a new base currency for all transactions.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            {/* Warning */}
            <div className="flex items-start gap-2 p-3 bg-amber-50 border border-amber-200 rounded-lg text-sm text-amber-800">
              <AlertTriangle className="h-4 w-4 mt-0.5 shrink-0" />
              <span>
                Changing the base currency affects all future transactions, reports, and balances.
                Existing records will retain their original currency. Proceed with caution.
              </span>
            </div>

            <div className="space-y-2">
              <Label>Currency</Label>
              <Select value={selectedCurrency} onValueChange={setSelectedCurrency}>
                <SelectTrigger>
                  <SelectValue placeholder="Select a currency" />
                </SelectTrigger>
                <SelectContent>
                  {Object.values(CURRENCIES).map((curr) => (
                    <SelectItem key={curr.code} value={curr.code}>
                      {curr.flag} {curr.name} ({curr.symbol})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {selectedPreview && (
              <div className="space-y-2 p-3 rounded-lg bg-muted/50 text-sm">
                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <p className="text-muted-foreground text-xs">Symbol</p>
                    <p className="font-medium text-lg">{selectedPreview.symbol}</p>
                  </div>
                  <div>
                    <p className="text-muted-foreground text-xs">ISO Code</p>
                    <p className="font-medium font-mono">{selectedPreview.code}</p>
                  </div>
                  <div>
                    <p className="text-muted-foreground text-xs">Country</p>
                    <p className="font-medium">{selectedPreview.flag} {selectedPreview.country}</p>
                  </div>
                  <div>
                    <p className="text-muted-foreground text-xs">Decimal Places</p>
                    <p className="font-medium">{selectedPreview.decimalPlaces}</p>
                  </div>
                </div>
              </div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowChangeDialog(false)}>
              Cancel
            </Button>
            <Button
              onClick={handleSaveCurrency}
              disabled={updateMutation.isPending || selectedCurrency === baseCurrency}
            >
              {updateMutation.isPending ? "Saving..." : "Save Currency"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </DashboardLayout>
  );
};

export default CurrenciesFxPage;
