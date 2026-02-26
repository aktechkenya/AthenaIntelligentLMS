import { useState } from "react";
import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Progress } from "@/components/ui/progress";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { Check, Lock, Loader2, Building2, Globe, DollarSign, GitBranch, BookOpen, CreditCard, UserCog, ChevronRight, Upload } from "lucide-react";
import { countries } from "@/data/regionConfig";

const steps = [
  { id: 1, label: "Institution Identity", icon: Building2 },
  { id: 2, label: "Country Entities", icon: Globe },
  { id: 3, label: "Currencies & FX Rates", icon: DollarSign },
  { id: 4, label: "Branch Hierarchy", icon: GitBranch },
  { id: 5, label: "Chart of Accounts", icon: BookOpen },
  { id: 6, label: "Payment Rails", icon: CreditCard },
  { id: 7, label: "Admin User & Activation", icon: UserCog },
];

const availableCountries = [
  { code: "KEN", name: "Kenya", flag: "🇰🇪", currency: "KES", timezone: "Africa/Nairobi", regulator: "Central Bank of Kenya", vat: 16, wht: 15, excise: 20, rails: ["M-Pesa", "Airtel", "RTGS"] },
  { code: "UGA", name: "Uganda", flag: "🇺🇬", currency: "UGX", timezone: "Africa/Kampala", regulator: "Bank of Uganda", vat: 18, wht: 15, excise: 12, rails: ["MTN MoMo", "Airtel", "RTGS"] },
  { code: "TZA", name: "Tanzania", flag: "🇹🇿", currency: "TZS", timezone: "Africa/Dar_es_Salaam", regulator: "Bank of Tanzania", vat: 18, wht: 10, excise: 10, rails: ["M-Pesa", "RTGS"] },
  { code: "RWA", name: "Rwanda", flag: "🇷🇼", currency: "RWF", timezone: "Africa/Kigali", regulator: "National Bank of Rwanda", vat: 18, wht: 15, excise: 0, rails: ["MTN MoMo", "RTGS"] },
  { code: "GHA", name: "Ghana", flag: "🇬🇭", currency: "GHS", timezone: "Africa/Accra", regulator: "Bank of Ghana", vat: 15, wht: 8, excise: 0, rails: ["MTN MoMo", "RTGS"] },
  { code: "NGA", name: "Nigeria", flag: "🇳🇬", currency: "NGN", timezone: "Africa/Lagos", regulator: "Central Bank of Nigeria", vat: 7.5, wht: 10, excise: 0, rails: ["NIBSS", "RTGS"] },
];

const months = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];

const coaTemplates = [
  { id: "banking", icon: "📊", title: "STANDARD BANKING CoA", desc: "IFRS-compliant", accounts: 120, categories: 6, best: "Banks, DFIs" },
  { id: "mfi", icon: "🤝", title: "MICROFINANCE CoA", desc: "MIX Market-compatible", accounts: 85, categories: 0, best: "MFIs, SACCOs" },
  { id: "digital", icon: "⚡", title: "DIGITAL LENDER CoA", desc: "Minimal setup", accounts: 60, categories: 0, best: "Fintechs, BNPL" },
];

export default function SetupWizardPage() {
  const [currentStep, setCurrentStep] = useState(1);
  const [selectedCountries, setSelectedCountries] = useState<string[]>(["KEN"]);
  const [selectedCoA, setSelectedCoA] = useState("");
  const [selectedMonth, setSelectedMonth] = useState("January");

  const progress = ((currentStep - 1) / (steps.length - 1)) * 100;

  const toggleCountry = (code: string) => {
    setSelectedCountries((prev) =>
      prev.includes(code) ? prev.filter((c) => c !== code) : [...prev, code]
    );
  };

  return (
    <DashboardLayout title="Setup Wizard" subtitle="Configure your AthenaLMS institution">
      <div className="flex gap-6">
        {/* Step sidebar */}
        <div className="w-60 shrink-0">
          <div className="mb-4">
            <Progress value={progress} className="h-2" />
            <p className="text-[10px] text-muted-foreground mt-1 font-sans">{Math.round(progress)}% complete</p>
          </div>
          <div className="space-y-1">
            {steps.map((step) => {
              const StepIcon = step.id < currentStep ? Check : step.id === currentStep ? step.icon : Lock;
              const isActive = step.id === currentStep;
              const isDone = step.id < currentStep;
              return (
                <button
                  key={step.id}
                  onClick={() => step.id <= currentStep && setCurrentStep(step.id)}
                  className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-md text-sm transition-colors ${
                    isActive ? "bg-primary text-primary-foreground" : isDone ? "text-success hover:bg-muted" : "text-muted-foreground"
                  }`}
                >
                  <StepIcon className="h-4 w-4 shrink-0" />
                  <span className="text-left">{step.label}</span>
                </button>
              );
            })}
          </div>
        </div>

        {/* Step content */}
        <div className="flex-1 min-w-0">
          {currentStep === 1 && (
            <Card>
              <CardHeader>
                <CardTitle className="text-lg">Institution Identity</CardTitle>
                <CardDescription>Enter your institution's legal and regulatory details</CardDescription>
              </CardHeader>
              <CardContent className="space-y-6">
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label>Legal Name</Label>
                    <Input placeholder="AthenaLMS Kenya Ltd" defaultValue="AthenaLMS Kenya Ltd" />
                  </div>
                  <div className="space-y-2">
                    <Label>Trading Name</Label>
                    <Input placeholder="AthenaLMS" defaultValue="AthenaLMS" />
                  </div>
                  <div className="space-y-2">
                    <Label>Registration Number</Label>
                    <Input placeholder="CPR/2018/123456" defaultValue="CPR/2018/123456" />
                  </div>
                  <div className="space-y-2">
                    <Label>Tax Identification Number</Label>
                    <Input placeholder="P051234567Z" defaultValue="P051234567Z" />
                  </div>
                  <div className="space-y-2">
                    <Label>Institution Type</Label>
                    <Select defaultValue="digital">
                      <SelectTrigger><SelectValue /></SelectTrigger>
                      <SelectContent>
                        <SelectItem value="bank">🏦 Commercial Bank</SelectItem>
                        <SelectItem value="mfi">🤝 Microfinance Institution</SelectItem>
                        <SelectItem value="digital">📱 Digital Lender</SelectItem>
                        <SelectItem value="sacco">🏘 SACCO</SelectItem>
                        <SelectItem value="dfi">🏛 Development Finance</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="space-y-2">
                    <Label>Regulator</Label>
                    <Input placeholder="Central Bank of Kenya" defaultValue="Central Bank of Kenya" />
                  </div>
                </div>
                <div className="space-y-2">
                  <Label>Head Office Address</Label>
                  <Input placeholder="Upper Hill, Nairobi, Kenya" defaultValue="Upper Hill, Nairobi, Kenya" />
                </div>
                <div className="space-y-2">
                  <Label>Logo Upload</Label>
                  <div className="border-2 border-dashed rounded-lg p-8 text-center hover:border-accent transition-colors cursor-pointer">
                    <Upload className="h-8 w-8 mx-auto text-muted-foreground mb-2" />
                    <p className="text-sm text-muted-foreground">Drag & drop or click to upload (512×512px recommended)</p>
                  </div>
                </div>
                <div className="space-y-2">
                  <Label>Fiscal Year Start Month</Label>
                  <div className="flex flex-wrap gap-2">
                    {months.map((m) => (
                      <button
                        key={m}
                        onClick={() => setSelectedMonth(m)}
                        className={`px-3 py-1.5 rounded-full text-xs font-medium transition-colors ${
                          selectedMonth === m ? "bg-primary text-primary-foreground" : "bg-muted hover:bg-muted/80 text-foreground"
                        }`}
                      >
                        {m.slice(0, 3)}
                      </button>
                    ))}
                  </div>
                </div>
              </CardContent>
            </Card>
          )}

          {currentStep === 2 && (
            <div className="space-y-4">
              <Card>
                <CardHeader>
                  <CardTitle className="text-lg">Country Entities</CardTitle>
                  <CardDescription>Which countries do you operate in? You can add more later.</CardDescription>
                </CardHeader>
              </Card>
              <div className="grid grid-cols-1 gap-3">
                {availableCountries.map((c) => {
                  const selected = selectedCountries.includes(c.code);
                  return (
                    <Card key={c.code} className={`transition-all ${selected ? "border-accent shadow-md" : ""}`}>
                      <CardContent className="p-4">
                        <div className="flex items-start justify-between">
                          <div className="flex items-center gap-3">
                            <span className="text-2xl">{c.flag}</span>
                            <div>
                              <h3 className="font-medium font-sans">{c.name}</h3>
                              <p className="text-xs text-muted-foreground">
                                Currency: {c.currency} • Timezone: {c.timezone}
                              </p>
                              {selected && (
                                <div className="mt-1 text-xs text-muted-foreground">
                                  Regulator: {c.regulator} • VAT: {c.vat}% • WHT: {c.wht}% • Excise: {c.excise}%
                                  <br />Payment Rails: {c.rails.join(", ")}
                                </div>
                              )}
                            </div>
                          </div>
                          <div className="flex items-center gap-2">
                            {selected ? (
                              <>
                                <Badge className="bg-success/15 text-success border-success/30">SELECTED</Badge>
                                <Button size="sm" variant="ghost" onClick={() => toggleCountry(c.code)} className="text-xs">Remove</Button>
                              </>
                            ) : (
                              <Button size="sm" variant="outline" onClick={() => toggleCountry(c.code)}>Enable This Country</Button>
                            )}
                          </div>
                        </div>
                      </CardContent>
                    </Card>
                  );
                })}
              </div>
            </div>
          )}

          {currentStep === 3 && (
            <Card>
              <CardHeader>
                <CardTitle className="text-lg">Currencies & FX Rates</CardTitle>
                <CardDescription>Configure active currencies and set initial exchange rates</CardDescription>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-2 gap-6">
                  <div>
                    <h4 className="text-sm font-medium mb-3 font-sans">Active Currencies</h4>
                    <div className="space-y-2">
                      {["KES", "UGX", "TZS", "USD", "EUR"].map((cur) => (
                        <div key={cur} className="flex items-center gap-2 px-3 py-2 bg-muted rounded-md">
                          <span className="text-sm font-mono font-medium">{cur}</span>
                          <span className="text-xs text-muted-foreground">
                            {cur === "KES" ? "Kenyan Shilling" : cur === "UGX" ? "Ugandan Shilling" : cur === "TZS" ? "Tanzanian Shilling" : cur === "USD" ? "US Dollar" : "Euro"}
                          </span>
                        </div>
                      ))}
                      <Button variant="outline" size="sm" className="w-full">+ Add Currency</Button>
                    </div>
                  </div>
                  <div>
                    <h4 className="text-sm font-medium mb-3 font-sans">Initial FX Rates (as of today)</h4>
                    <div className="space-y-3">
                      {[
                        { pair: "USD → KES", mid: "129.50", buy: "128.80", sell: "130.20" },
                        { pair: "USD → UGX", mid: "3,720", buy: "3,700", sell: "3,740" },
                        { pair: "USD → TZS", mid: "2,540", buy: "2,530", sell: "2,550" },
                        { pair: "EUR → USD", mid: "1.085", buy: "1.080", sell: "1.090" },
                      ].map((rate) => (
                        <div key={rate.pair} className="grid grid-cols-4 gap-2 items-center">
                          <span className="text-xs font-mono font-medium">{rate.pair}</span>
                          <Input defaultValue={rate.mid} className="h-8 text-xs font-mono" />
                          <Input defaultValue={rate.buy} className="h-8 text-xs font-mono" placeholder="Buy" />
                          <Input defaultValue={rate.sell} className="h-8 text-xs font-mono" placeholder="Sell" />
                        </div>
                      ))}
                    </div>
                    <div className="mt-4 flex items-center gap-4 text-xs">
                      <label className="flex items-center gap-2">
                        <input type="radio" name="rate-source" defaultChecked className="accent-primary" /> Manual entry
                      </label>
                      <label className="flex items-center gap-2">
                        <input type="radio" name="rate-source" className="accent-primary" /> Auto-fetch (API key req)
                      </label>
                    </div>
                  </div>
                </div>
              </CardContent>
            </Card>
          )}

          {currentStep === 4 && (
            <Card>
              <CardHeader>
                <CardTitle className="text-lg">Branch Hierarchy</CardTitle>
                <CardDescription>Build your branch tree structure</CardDescription>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-2 gap-6">
                  <div className="border rounded-lg p-4">
                    <h4 className="text-sm font-medium mb-3 font-sans">Branch Tree</h4>
                    <div className="space-y-1 text-sm font-sans">
                      <div className="flex items-center gap-2 py-1.5 px-2 rounded bg-muted">
                        <span>🏛</span> <span className="font-medium">Nairobi HQ</span>
                        <Badge variant="outline" className="text-[10px] ml-auto">Head Office</Badge>
                      </div>
                      <div className="ml-6 space-y-1">
                        <div className="flex items-center gap-2 py-1.5 px-2 rounded hover:bg-muted">
                          <span>🏦</span> <span>Westlands Branch</span>
                        </div>
                        <div className="ml-6">
                          <div className="flex items-center gap-2 py-1.5 px-2 rounded hover:bg-muted">
                            <span>🏪</span> <span>Karen Sub-Branch</span>
                          </div>
                        </div>
                        <div className="flex items-center gap-2 py-1.5 px-2 rounded hover:bg-muted">
                          <span>🏦</span> <span>Mombasa Branch</span>
                        </div>
                      </div>
                      <div className="flex items-center gap-2 py-1.5 px-2 rounded hover:bg-muted mt-2">
                        <span>🏛</span> <span className="font-medium">Kampala HQ</span>
                        <Badge variant="outline" className="text-[10px] ml-auto">Head Office</Badge>
                      </div>
                      <div className="ml-6">
                        <div className="flex items-center gap-2 py-1.5 px-2 rounded hover:bg-muted">
                          <span>🏦</span> <span>Mbarara Branch</span>
                        </div>
                      </div>
                      <div className="flex items-center gap-2 py-1.5 px-2 rounded hover:bg-muted mt-2">
                        <span>🏛</span> <span className="font-medium">Dar es Salaam HQ</span>
                        <Badge variant="outline" className="text-[10px] ml-auto">Head Office</Badge>
                      </div>
                    </div>
                    <div className="mt-4 flex gap-2">
                      <Button size="sm" variant="outline">+ Add Branch</Button>
                      <Button size="sm" variant="ghost">Import from CSV</Button>
                    </div>
                  </div>
                  <div className="border rounded-lg p-4">
                    <h4 className="text-sm font-medium mb-3 font-sans">Branch Details</h4>
                    <p className="text-xs text-muted-foreground mb-4">Select a branch from the tree to edit, or click "Add Branch"</p>
                    <div className="space-y-3">
                      <div className="grid grid-cols-2 gap-3">
                        <div className="space-y-1">
                          <Label className="text-xs">Branch Name</Label>
                          <Input className="h-8 text-xs" placeholder="Branch name" />
                        </div>
                        <div className="space-y-1">
                          <Label className="text-xs">Branch Code</Label>
                          <Input className="h-8 text-xs" placeholder="Auto-generated" />
                        </div>
                      </div>
                      <div className="space-y-1">
                        <Label className="text-xs">Branch Type</Label>
                        <Select>
                          <SelectTrigger className="h-8 text-xs"><SelectValue placeholder="Select type" /></SelectTrigger>
                          <SelectContent>
                            <SelectItem value="head">Head Office</SelectItem>
                            <SelectItem value="full">Full Branch</SelectItem>
                            <SelectItem value="sub">Sub-Branch</SelectItem>
                            <SelectItem value="agency">Agency</SelectItem>
                            <SelectItem value="mobile">Mobile Unit</SelectItem>
                            <SelectItem value="digital">Digital Only</SelectItem>
                          </SelectContent>
                        </Select>
                      </div>
                      <div className="space-y-1">
                        <Label className="text-xs">City & Address</Label>
                        <Input className="h-8 text-xs" placeholder="City, Address" />
                      </div>
                      <div className="space-y-1">
                        <Label className="text-xs">Branch Manager</Label>
                        <Input className="h-8 text-xs" placeholder="Search users..." />
                      </div>
                      <div className="grid grid-cols-2 gap-3">
                        <div className="space-y-1">
                          <Label className="text-xs">Max Approval Amount</Label>
                          <Input className="h-8 text-xs font-mono" placeholder="0.00" />
                        </div>
                        <div className="space-y-1">
                          <Label className="text-xs">Sort Code</Label>
                          <Input className="h-8 text-xs font-mono" placeholder="01001" />
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </CardContent>
            </Card>
          )}

          {currentStep === 5 && (
            <div className="space-y-4">
              <Card>
                <CardHeader>
                  <CardTitle className="text-lg">Chart of Accounts</CardTitle>
                  <CardDescription>Select a template to get started quickly</CardDescription>
                </CardHeader>
              </Card>
              <div className="grid grid-cols-3 gap-4">
                {coaTemplates.map((t) => (
                  <Card
                    key={t.id}
                    className={`cursor-pointer transition-all hover:shadow-md ${selectedCoA === t.id ? "border-accent shadow-md" : ""}`}
                    onClick={() => setSelectedCoA(t.id)}
                  >
                    <CardContent className="p-6 text-center">
                      <span className="text-3xl">{t.icon}</span>
                      <h3 className="font-heading text-base mt-3">{t.title}</h3>
                      <p className="text-xs text-muted-foreground mt-1">{t.desc}</p>
                      <p className="text-xs text-muted-foreground">{t.accounts} accounts</p>
                      <p className="text-xs text-muted-foreground mt-1">Best for: {t.best}</p>
                      {selectedCoA === t.id && (
                        <Badge className="mt-3 bg-success/15 text-success border-success/30">Selected</Badge>
                      )}
                    </CardContent>
                  </Card>
                ))}
              </div>
            </div>
          )}

          {currentStep === 6 && (
            <Card>
              <CardHeader>
                <CardTitle className="text-lg">Payment Rails</CardTitle>
                <CardDescription>Configure payment integrations per country</CardDescription>
              </CardHeader>
              <CardContent>
                <h4 className="text-sm font-medium mb-3">🇰🇪 Kenya Payment Rails</h4>
                <div className="grid grid-cols-2 gap-3 mb-6">
                  {[
                    { icon: "📱", name: "M-Pesa (Daraja)", features: ["Disbursement", "Collection"], status: "not_set" },
                    { icon: "📱", name: "Airtel Money", features: ["Disbursement", "Collection"], status: "not_set" },
                    { icon: "🏦", name: "Bank Transfer", features: ["SWIFT/ACH"], status: "not_set" },
                    { icon: "🏛", name: "RTGS", features: ["High value same-day"], status: "configured" },
                  ].map((rail) => (
                    <Card key={rail.name}>
                      <CardContent className="p-4">
                        <div className="flex items-center gap-2 mb-2">
                          <span>{rail.icon}</span>
                          <span className="font-medium text-sm font-sans">{rail.name}</span>
                        </div>
                        <div className="flex flex-wrap gap-1 mb-3">
                          {rail.features.map((f) => (
                            <Badge key={f} variant="outline" className="text-[10px]">{f} ✓</Badge>
                          ))}
                        </div>
                        <div className="flex items-center justify-between">
                          <Button size="sm" variant="outline" className="text-xs">Configure API Keys</Button>
                          <span className={`text-[10px] flex items-center gap-1 ${rail.status === "configured" ? "text-success" : "text-muted-foreground"}`}>
                            <span className={`h-1.5 w-1.5 rounded-full ${rail.status === "configured" ? "bg-success" : "bg-muted-foreground"}`} />
                            {rail.status === "configured" ? "Configured" : "Not set"}
                          </span>
                        </div>
                      </CardContent>
                    </Card>
                  ))}
                </div>
              </CardContent>
            </Card>
          )}

          {currentStep === 7 && (
            <Card>
              <CardHeader>
                <CardTitle className="text-lg">Admin User & Activation</CardTitle>
                <CardDescription>Create the first administrator and activate your institution</CardDescription>
              </CardHeader>
              <CardContent className="space-y-6">
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label>Full Name</Label>
                    <Input defaultValue="John Mwangi" />
                  </div>
                  <div className="space-y-2">
                    <Label>Email</Label>
                    <Input type="email" defaultValue="john@athenalms.com" />
                  </div>
                  <div className="space-y-2">
                    <Label>Phone Number</Label>
                    <Input defaultValue="+254 720 000 000" />
                  </div>
                  <div className="space-y-2">
                    <Label>Password</Label>
                    <Input type="password" defaultValue="strongpassword" />
                    <div className="h-1.5 rounded-full bg-muted overflow-hidden">
                      <div className="h-full w-4/5 bg-success rounded-full" />
                    </div>
                    <p className="text-[10px] text-success">Strong</p>
                  </div>
                </div>
                <div className="border rounded-lg p-4 bg-muted/30">
                  <h4 className="text-sm font-medium mb-2 font-sans">Setup Summary</h4>
                  <div className="grid grid-cols-4 gap-4 text-xs">
                    <div><span className="text-muted-foreground">Countries:</span> <span className="font-medium">{selectedCountries.length}</span></div>
                    <div><span className="text-muted-foreground">Currencies:</span> <span className="font-medium">5</span></div>
                    <div><span className="text-muted-foreground">Branches:</span> <span className="font-medium">7</span></div>
                    <div><span className="text-muted-foreground">CoA Template:</span> <span className="font-medium">{selectedCoA || "Not selected"}</span></div>
                  </div>
                </div>
                <Button
                  className="w-full"
                  onClick={() => {
                    localStorage.setItem("athena_setup_complete", "true");
                  }}
                >
                  🚀 Activate Institution
                </Button>
              </CardContent>
            </Card>
          )}

          {/* Navigation */}
          <div className="flex justify-between mt-6">
            <Button variant="outline" disabled={currentStep === 1} onClick={() => setCurrentStep((s) => s - 1)}>
              Back
            </Button>
            <Button onClick={() => setCurrentStep((s) => Math.min(s + 1, 7))} disabled={currentStep === 7}>
              Continue <ChevronRight className="h-4 w-4 ml-1" />
            </Button>
          </div>
        </div>
      </div>
    </DashboardLayout>
  );
}
