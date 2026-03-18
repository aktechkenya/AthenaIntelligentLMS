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
import { Loader2, Globe, Clock, Info, Landmark, MapPin, Pencil } from "lucide-react";

// ── country data ───────────────────────────────────────────────────────────

interface CountryDetail {
  name: string;
  flag: string;
  region: string;
  capital: string;
  timezone: string;
  regulatoryBody: string;
  regulatoryAbbr: string;
  currencyCode: string;
  currencyName: string;
  centralBank: string;
}

const COUNTRIES: Record<string, CountryDetail> = {
  KEN: {
    name: "Kenya",
    flag: "\u{1F1F0}\u{1F1EA}",
    region: "East Africa",
    capital: "Nairobi",
    timezone: "Africa/Nairobi",
    regulatoryBody: "Central Bank of Kenya",
    regulatoryAbbr: "CBK",
    currencyCode: "KES",
    currencyName: "Kenyan Shilling",
    centralBank: "Central Bank of Kenya (CBK)",
  },
  UGA: {
    name: "Uganda",
    flag: "\u{1F1FA}\u{1F1EC}",
    region: "East Africa",
    capital: "Kampala",
    timezone: "Africa/Kampala",
    regulatoryBody: "Bank of Uganda",
    regulatoryAbbr: "BoU",
    currencyCode: "UGX",
    currencyName: "Ugandan Shilling",
    centralBank: "Bank of Uganda (BoU)",
  },
  TZA: {
    name: "Tanzania",
    flag: "\u{1F1F9}\u{1F1FF}",
    region: "East Africa",
    capital: "Dodoma",
    timezone: "Africa/Dar_es_Salaam",
    regulatoryBody: "Bank of Tanzania",
    regulatoryAbbr: "BoT",
    currencyCode: "TZS",
    currencyName: "Tanzanian Shilling",
    centralBank: "Bank of Tanzania (BoT)",
  },
  GHA: {
    name: "Ghana",
    flag: "\u{1F1EC}\u{1F1ED}",
    region: "West Africa",
    capital: "Accra",
    timezone: "Africa/Accra",
    regulatoryBody: "Bank of Ghana",
    regulatoryAbbr: "BoG",
    currencyCode: "GHS",
    currencyName: "Ghanaian Cedi",
    centralBank: "Bank of Ghana (BoG)",
  },
  NGA: {
    name: "Nigeria",
    flag: "\u{1F1F3}\u{1F1EC}",
    region: "West Africa",
    capital: "Abuja",
    timezone: "Africa/Lagos",
    regulatoryBody: "Central Bank of Nigeria",
    regulatoryAbbr: "CBN",
    currencyCode: "NGN",
    currencyName: "Nigerian Naira",
    centralBank: "Central Bank of Nigeria (CBN)",
  },
};

// ── component ──────────────────────────────────────────────────────────────

const CountryConfigPage = () => {
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const [showChangeDialog, setShowChangeDialog] = useState(false);
  const [selectedCountry, setSelectedCountry] = useState("");

  const { data: settings, isLoading, isError } = useQuery({
    queryKey: ["org", "settings"],
    queryFn: () => orgService.getSettings(),
  });

  const countryCode = settings?.countryCode ?? "KEN";
  const countryInfo = COUNTRIES[countryCode] ?? COUNTRIES.KEN;

  const updateMutation = useMutation({
    mutationFn: (code: string) => {
      const country = COUNTRIES[code];
      return orgService.updateSettings({
        countryCode: code,
        timezone: country.timezone,
      });
    },
    onSuccess: (_data, code) => {
      queryClient.invalidateQueries({ queryKey: ["org", "settings"] });
      const country = COUNTRIES[code];
      toast({
        title: "Country Updated",
        description: `Operating country changed to ${country.name}`,
      });
      setShowChangeDialog(false);
    },
    onError: (err: Error) => {
      toast({ title: "Update Failed", description: err.message, variant: "destructive" });
    },
  });

  const handleSaveCountry = () => {
    if (!selectedCountry) return;
    updateMutation.mutate(selectedCountry);
  };

  const openChangeDialog = () => {
    setSelectedCountry(countryCode);
    setShowChangeDialog(true);
  };

  const selectedPreview = selectedCountry ? COUNTRIES[selectedCountry] : null;

  return (
    <DashboardLayout
      title="Country Configuration"
      subtitle="Country, region, and timezone settings for this organization"
    >
      {isLoading && (
        <div className="flex items-center justify-center h-64 text-muted-foreground">
          <Loader2 className="h-6 w-6 animate-spin mr-2" />
          <span>Loading country configuration...</span>
        </div>
      )}

      {isError && (
        <div className="flex items-center justify-center h-64 text-destructive">
          <p>Failed to load country configuration. Please try again.</p>
        </div>
      )}

      {settings && (
        <div className="space-y-6">
          {/* Country Card */}
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle className="text-base">Operating Country</CardTitle>
                <Button variant="outline" size="sm" className="gap-2" onClick={openChangeDialog}>
                  <Pencil className="h-3.5 w-3.5" />
                  Change Country
                </Button>
              </div>
            </CardHeader>
            <CardContent>
              <div className="flex items-center justify-between p-4 border rounded-lg bg-muted/30">
                <div className="flex items-center gap-4">
                  <div className="flex items-center justify-center w-12 h-12 rounded-full bg-primary/10 text-2xl">
                    {countryInfo.flag}
                  </div>
                  <div>
                    <p className="font-semibold">{countryInfo.name}</p>
                    <p className="text-sm text-muted-foreground">
                      {countryInfo.region} &mdash; ISO 3166-1: {countryCode}
                    </p>
                  </div>
                </div>
                <Badge variant="default">Active</Badge>
              </div>
            </CardContent>
          </Card>

          {/* Country Details */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="text-sm font-medium text-muted-foreground flex items-center gap-2">
                  <MapPin className="h-4 w-4" />
                  Capital
                </CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-lg font-semibold">{countryInfo.capital}</p>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="text-sm font-medium text-muted-foreground flex items-center gap-2">
                  <Clock className="h-4 w-4" />
                  Timezone
                </CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-lg font-semibold">{settings.timezone}</p>
                <p className="text-xs text-muted-foreground mt-1">
                  All transaction timestamps use this timezone
                </p>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="text-sm font-medium text-muted-foreground flex items-center gap-2">
                  <Landmark className="h-4 w-4" />
                  Regulatory Body
                </CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-lg font-semibold">{countryInfo.regulatoryAbbr}</p>
                <p className="text-xs text-muted-foreground mt-1">{countryInfo.regulatoryBody}</p>
              </CardContent>
            </Card>
          </div>

          {/* Regulatory Information Table */}
          <Card>
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <Landmark className="h-4 w-4 text-primary" />
                Regulatory Information by Country
              </CardTitle>
            </CardHeader>
            <CardContent className="p-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Country</TableHead>
                    <TableHead>Regulator</TableHead>
                    <TableHead>Central Bank</TableHead>
                    <TableHead>Currency</TableHead>
                    <TableHead>Timezone</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {Object.entries(COUNTRIES).map(([code, info]) => (
                    <TableRow key={code} className={code === countryCode ? "bg-primary/5" : ""}>
                      <TableCell className="font-medium">
                        <span className="mr-2">{info.flag}</span>
                        {info.name}
                        {code === countryCode && (
                          <Badge variant="outline" className="ml-2 text-xs">Current</Badge>
                        )}
                      </TableCell>
                      <TableCell>{info.regulatoryBody}</TableCell>
                      <TableCell>{info.centralBank}</TableCell>
                      <TableCell>
                        <span className="font-mono text-sm">{info.currencyCode}</span>
                        <span className="text-muted-foreground ml-1 text-xs">({info.currencyName})</span>
                      </TableCell>
                      <TableCell className="font-mono text-sm">{info.timezone}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>

          {/* Info Note */}
          <div className="flex items-start gap-2 p-3 bg-blue-50 border border-blue-200 rounded-lg text-sm text-blue-800">
            <Info className="h-4 w-4 mt-0.5 shrink-0" />
            <span>
              Changing the operating country will update the timezone for all future transactions.
              Existing transaction timestamps are not affected.
            </span>
          </div>
        </div>
      )}

      {/* Change Country Dialog */}
      <Dialog open={showChangeDialog} onOpenChange={setShowChangeDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Change Operating Country</DialogTitle>
            <DialogDescription>
              Select a new operating country. The timezone will be updated automatically.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <Label>Country</Label>
              <Select value={selectedCountry} onValueChange={setSelectedCountry}>
                <SelectTrigger>
                  <SelectValue placeholder="Select a country" />
                </SelectTrigger>
                <SelectContent>
                  {Object.entries(COUNTRIES).map(([code, info]) => (
                    <SelectItem key={code} value={code}>
                      {info.flag} {info.name} ({code})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {selectedPreview && (
              <div className="space-y-2 p-3 rounded-lg bg-muted/50 text-sm">
                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <p className="text-muted-foreground text-xs">Capital</p>
                    <p className="font-medium">{selectedPreview.capital}</p>
                  </div>
                  <div>
                    <p className="text-muted-foreground text-xs">Region</p>
                    <p className="font-medium">{selectedPreview.region}</p>
                  </div>
                  <div>
                    <p className="text-muted-foreground text-xs">Timezone</p>
                    <p className="font-medium">{selectedPreview.timezone}</p>
                  </div>
                  <div>
                    <p className="text-muted-foreground text-xs">Regulator</p>
                    <p className="font-medium">{selectedPreview.regulatoryAbbr}</p>
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
              onClick={handleSaveCountry}
              disabled={updateMutation.isPending || selectedCountry === countryCode}
            >
              {updateMutation.isPending ? "Saving..." : "Save Country"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </DashboardLayout>
  );
};

export default CountryConfigPage;
