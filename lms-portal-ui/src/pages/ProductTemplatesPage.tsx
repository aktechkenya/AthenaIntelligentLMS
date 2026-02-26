import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { productTemplates } from "@/data/productConfig";
import { useNavigate } from "react-router-dom";
import { motion } from "framer-motion";
import { ArrowRight } from "lucide-react";

const ProductTemplatesPage = () => {
  const navigate = useNavigate();

  return (
    <DashboardLayout
      title="Product Templates"
      subtitle="Pre-configured product blueprints — clone and customise"
      breadcrumbs={[{ label: "Home", href: "/" }, { label: "Products" }, { label: "Product Templates" }]}
    >
      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
        {productTemplates.map((tpl, i) => (
          <motion.div
            key={tpl.id}
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.06, duration: 0.3 }}
          >
            <Card className="hover:shadow-md hover:border-accent/30 transition-all h-full flex flex-col">
              <CardContent className="p-5 flex flex-col flex-1">
                <div className="flex items-center gap-3 mb-3">
                  <span className="text-3xl">{tpl.icon}</span>
                  <div>
                    <h3 className="text-sm font-sans font-semibold leading-tight">{tpl.name}</h3>
                    <Badge variant="outline" className="text-[9px] mt-1 font-sans">Template</Badge>
                  </div>
                </div>

                <p className="text-xs text-muted-foreground font-sans mb-3 flex-1">{tpl.description}</p>

                <div className="bg-muted/50 rounded-md px-3 py-2 mb-4">
                  <p className="text-[10px] text-muted-foreground font-sans uppercase tracking-wider mb-0.5">Key Parameters</p>
                  <p className="text-xs font-mono font-medium">{tpl.keyParams}</p>
                </div>

                <Button
                  size="sm"
                  className="w-full text-xs font-sans"
                  onClick={() => navigate("/product-config")}
                >
                  Use Template <ArrowRight className="ml-1.5 h-3.5 w-3.5" />
                </Button>
              </CardContent>
            </Card>
          </motion.div>
        ))}
      </div>
    </DashboardLayout>
  );
};

export default ProductTemplatesPage;
