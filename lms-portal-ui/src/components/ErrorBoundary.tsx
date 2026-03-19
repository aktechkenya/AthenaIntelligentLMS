import React from "react";
import { AlertTriangle } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

interface Props {
  children: React.ReactNode;
  fallback?: React.ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends React.Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error("[ErrorBoundary]", error, info.componentStack);
  }

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) return this.props.fallback;
      return (
        <Card className="m-4">
          <CardContent className="flex flex-col items-center justify-center gap-3 py-12">
            <AlertTriangle className="h-8 w-8 text-destructive" />
            <p className="text-sm font-semibold">Something went wrong</p>
            <p className="text-xs text-muted-foreground max-w-md text-center">
              {this.state.error?.message ?? "An unexpected error occurred while rendering this page."}
            </p>
            <Button
              size="sm"
              variant="outline"
              className="text-xs mt-2"
              onClick={() => this.setState({ hasError: false, error: null })}
            >
              Try Again
            </Button>
          </CardContent>
        </Card>
      );
    }
    return this.props.children;
  }
}
