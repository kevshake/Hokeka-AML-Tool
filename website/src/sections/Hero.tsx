import { ArrowRight, Play, Shield, Lock, Eye, Zap } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Spotlight } from "@/components/ui/spotlight";
import { SplineScene } from "@/components/ui/spline-scene";

export default function Hero() {
  return (
    <section className="relative min-h-screen overflow-hidden bg-gradient-to-br from-burgundy-950 via-burgundy-900 to-burgundy-800">
      {/* Background Pattern */}
      <div className="absolute inset-0 opacity-10">
        <div
          className="absolute inset-0"
          style={{
            backgroundImage: `url("data:image/svg+xml,%3Csvg width='60' height='60' viewBox='0 0 60 60' xmlns='http://www.w3.org/2000/svg'%3E%3Cg fill='none' fill-rule='evenodd'%3E%3Cg fill='%23FFD700' fill-opacity='0.4'%3E%3Cpath d='M36 34v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zm0-30V0h-2v4h-4v2h4v4h2V6h4V4h-4zM6 34v-4H4v4H0v2h4v4h2v-4h4v-2H6zM6 4V0H4v4H0v2h4v4h2V6h4V4H6z'/%3E%3C/g%3E%3C/g%3E%3C/svg%3E")`,
          }}
        />
      </div>

      {/* Gradient Overlay */}
      <div className="absolute inset-0 bg-gradient-to-b from-transparent via-burgundy-900/30 to-burgundy-950/80" />

      <div className="relative z-10 section-padding pt-24 pb-16 lg:pt-32 lg:pb-20">
        <div className="max-w-7xl mx-auto">
          <div className="grid lg:grid-cols-2 gap-8 lg:gap-12 items-center">
            {/* Left Content */}
            <div className="text-center lg:text-left order-2 lg:order-1">
              <div className="inline-flex items-center gap-2 px-4 py-2 bg-white/10 rounded-full mb-6 border border-gold-500/30">
                <span className="w-2 h-2 bg-green-400 rounded-full animate-pulse" />
                <span className="text-gold-300 text-sm font-medium">Next-Gen AML Compliance</span>
              </div>

              <h1 className="text-4xl sm:text-5xl lg:text-6xl xl:text-7xl font-bold text-white leading-tight mb-6">
                AI-Powered
                <span className="block text-gradient-gold">Fraud Detection</span>
              </h1>

              <p className="text-lg sm:text-xl text-white/80 mb-8 max-w-xl mx-auto lg:mx-0">
                HOKEKA delivers intelligent Anti-Money Laundering solutions that
                protect your business, ensure regulatory compliance, and build
                customer trust across Africa and beyond.
              </p>

              <div className="flex flex-col sm:flex-row gap-4 justify-center lg:justify-start">
                <a
                  href="#features"
                  className="btn-primary flex items-center justify-center gap-2"
                >
                  Explore Features
                  <ArrowRight className="w-5 h-5" />
                </a>
                <a
                  href="#how-it-works"
                  className="btn-secondary flex items-center justify-center gap-2"
                >
                  <Play className="w-5 h-5" />
                  See How It Works
                </a>
              </div>

              {/* Trust Badges */}
              <div className="mt-12 pt-8 border-t border-white/10">
                <p className="text-white/60 text-sm mb-4">
                  Trusted by leading financial institutions
                </p>
                <div className="flex flex-wrap justify-center lg:justify-start gap-6 opacity-60">
                  {["Banking", "Fintech", "Insurance", "Crypto"].map(
                    (industry) => (
                      <span
                        key={industry}
                        className="text-white/70 font-semibold"
                      >
                        {industry}
                      </span>
                    )
                  )}
                </div>
              </div>
            </div>

            {/* Right Content - Spline 3D Scene */}
            <div className="relative order-1 lg:order-2">
              <Card className="w-full h-[400px] lg:h-[500px] bg-black/[0.96] relative overflow-hidden border-gold-500/20">
                <Spotlight
                  className="-top-40 left-0 md:left-60 md:-top-20"
                  fill="white"
                />

                <div className="flex h-full relative">
                  {/* Left content overlay */}
                  <div className="absolute top-0 left-0 p-6 lg:p-8 z-10 max-w-[200px] lg:max-w-[250px]">
                    <div className="flex items-center gap-2 mb-4">
                      <div className="flex gap-1">
                        <div className="w-2 h-2 lg:w-3 lg:h-3 rounded-full bg-red-500" />
                        <div className="w-2 h-2 lg:w-3 lg:h-3 rounded-full bg-yellow-500" />
                        <div className="w-2 h-2 lg:w-3 lg:h-3 rounded-full bg-green-500" />
                      </div>
                      <span className="ml-2 text-gold-400 font-semibold text-xs lg:text-sm">
                        Live Monitor
                      </span>
                    </div>

                    <div className="space-y-3">
                      <div className="bg-white/5 backdrop-blur-sm rounded-lg p-3 border border-white/10">
                        <div className="flex items-center gap-2 text-white/80 text-xs">
                          <Shield className="w-3 h-3 text-green-400" />
                          <span>Secure Connection</span>
                        </div>
                      </div>

                      <div className="bg-white/5 backdrop-blur-sm rounded-lg p-3 border border-white/10">
                        <div className="flex items-center gap-2 text-white/80 text-xs">
                          <Lock className="w-3 h-3 text-gold-400" />
                          <span>Encrypted Data</span>
                        </div>
                      </div>

                      <div className="bg-white/5 backdrop-blur-sm rounded-lg p-3 border border-white/10">
                        <div className="flex items-center gap-2 text-white/80 text-xs">
                          <Eye className="w-3 h-3 text-blue-400" />
                          <span>Real-time Monitoring</span>
                        </div>
                      </div>

                      <div className="bg-white/5 backdrop-blur-sm rounded-lg p-3 border border-white/10">
                        <div className="flex items-center gap-2 text-white/80 text-xs">
                          <Zap className="w-3 h-3 text-yellow-400" />
                          <span>AI Processing</span>
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Spline 3D Scene */}
                  <div className="flex-1 relative">
                    <SplineScene
                      scene="https://prod.spline.design/kZDDjO5HuC9GJUM2/scene.splinecode"
                      className="w-full h-full"
                    />
                  </div>
                </div>
              </Card>

              {/* Decorative Elements */}
              <div className="absolute -top-4 -right-4 w-24 h-24 bg-gold-500/20 rounded-full blur-2xl" />
              <div className="absolute -bottom-4 -left-4 w-32 h-32 bg-burgundy-400/20 rounded-full blur-3xl" />
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
