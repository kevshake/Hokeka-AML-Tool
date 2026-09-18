/**
 * Report Chart Component
 * Displays charts for analytical reports
 */

import { useMemo } from "react";
import {
  Box,
  Typography,
  ToggleButtonGroup,
  ToggleButton,
  Paper,
} from "@mui/material";
import {
  BarChart,
  Bar,
  LineChart,
  Line,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  AreaChart,
  Area,
} from "recharts";
import { useState } from "react";

import type { ChartDataResponse } from "../../../features/api/reportQueries";

type ChartType = "bar" | "line" | "pie" | "area";

interface ReportChartProps {
  data: Record<string, unknown> | ChartDataResponse;
  chartType?: ChartType;
  title?: string;
}

/*
 * Categorical series, ordered for maximum hue separation. Distinguished by hue
 * rather than luminance, so every entry sits between 6.3:1 and 9.4:1 on the card
 * surface and none of them disappears. Literal hexes: recharts writes these into
 * SVG fills, and a `var()` cannot be interpolated by its animation layer.
 */
const COLORS = [
  "#d3b371", // gold
  "#75b7ab", // teal
  "#e08a4f", // rust
  "#84a9c4", // steel
  "#b094c2", // plum
  "#e8776b", // coral
  "#e2b25d", // amber
  "#9cb583", // sage
];

const AXIS_TICK = { fontSize: 11, fill: "#a8aba8" };
const AXIS_LINE = { stroke: "rgba(255,255,255,0.12)" };
const LEGEND_STYLE = { fontSize: 11, color: "#a8aba8" };
const TOOLTIP_CONTENT = {
  backgroundColor: "#191c1b",
  border: "1px solid rgba(255,255,255,0.12)",
  borderRadius: "3px",
  fontSize: "12px",
  color: "#f5f2eb",
};

export default function ReportChart({
  data,
  chartType: defaultChartType = "bar",
  title,
}: ReportChartProps) {
  const [chartType, setChartType] = useState<ChartType>(defaultChartType);

  const chartData = useMemo(() => {
    if (!data || !data.data) return [];
    return (data.data as Record<string, unknown>[]) || [];
  }, [data]);

  const keys = useMemo(() => {
    if (chartData.length === 0) return [];
    return Object.keys(chartData[0]).filter((k) => k !== "name" && k !== "label" && k !== "category");
  }, [chartData]);

  const handleChartTypeChange = (
    _event: React.MouseEvent<HTMLElement>,
    newType: ChartType | null
  ) => {
    if (newType) {
      setChartType(newType);
    }
  };

  if (!data || chartData.length === 0) {
    return (
      <Box sx={{ p: 4, textAlign: "center" }}>
        <Typography color="text.secondary">No chart data available</Typography>
      </Box>
    );
  }

  const renderChart = () => {
    switch (chartType) {
      case "line":
        return (
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.08)" vertical={false} />
              <XAxis dataKey="name" tick={AXIS_TICK} axisLine={AXIS_LINE} tickLine={AXIS_LINE} />
              <YAxis tick={AXIS_TICK} axisLine={AXIS_LINE} tickLine={AXIS_LINE} />
              <Tooltip
                contentStyle={TOOLTIP_CONTENT} itemStyle={{ color: "#f5f2eb" }} labelStyle={{ color: "#a8aba8" }} cursor={{ fill: "rgba(255,255,255,0.04)" }}
              />
              <Legend wrapperStyle={LEGEND_STYLE} />
              {keys.map((key, index) => (
                <Line
                  key={key}
                  type="monotone"
                  dataKey={key}
                  stroke={COLORS[index % COLORS.length]}
                  strokeWidth={2}
                  dot={{ fill: COLORS[index % COLORS.length], strokeWidth: 2 }}
                />
              ))}
            </LineChart>
          </ResponsiveContainer>
        );

      case "area":
        return (
          <ResponsiveContainer width="100%" height={300}>
            <AreaChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.08)" vertical={false} />
              <XAxis dataKey="name" tick={AXIS_TICK} axisLine={AXIS_LINE} tickLine={AXIS_LINE} />
              <YAxis tick={AXIS_TICK} axisLine={AXIS_LINE} tickLine={AXIS_LINE} />
              <Tooltip
                contentStyle={TOOLTIP_CONTENT} itemStyle={{ color: "#f5f2eb" }} labelStyle={{ color: "#a8aba8" }} cursor={{ fill: "rgba(255,255,255,0.04)" }}
              />
              <Legend wrapperStyle={LEGEND_STYLE} />
              {keys.map((key, index) => (
                <Area
                  key={key}
                  type="monotone"
                  dataKey={key}
                  stroke={COLORS[index % COLORS.length]}
                  fill={COLORS[index % COLORS.length]}
                  fillOpacity={0.3}
                />
              ))}
            </AreaChart>
          </ResponsiveContainer>
        );

      case "pie": {
        const pieData = chartData.map((item) => ({
          name: item.name || item.label || item.category,
          value: item.value || item.count || item.amount || Object.values(item)[1],
        }));
        return (
          <ResponsiveContainer width="100%" height={300}>
            <PieChart>
              <Pie
                data={pieData}
                cx="50%"
                cy="50%"
                labelLine={false}
                label={({ name, percent }) =>
                  `${name}: ${((percent || 0) * 100).toFixed(0)}%`
                }
                outerRadius={100}
                fill="#b094c2"
                dataKey="value"
              >
                {pieData.map((_, index) => (
                  <Cell
                    key={`cell-${index}`}
                    fill={COLORS[index % COLORS.length]}
                  />
                ))}
              </Pie>
              <Tooltip
                contentStyle={TOOLTIP_CONTENT} itemStyle={{ color: "#f5f2eb" }} labelStyle={{ color: "#a8aba8" }} cursor={{ fill: "rgba(255,255,255,0.04)" }}
              />
              <Legend wrapperStyle={LEGEND_STYLE} />
            </PieChart>
          </ResponsiveContainer>
        );
      }

      case "bar":
      default:
        return (
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.08)" vertical={false} />
              <XAxis dataKey="name" tick={AXIS_TICK} axisLine={AXIS_LINE} tickLine={AXIS_LINE} />
              <YAxis tick={AXIS_TICK} axisLine={AXIS_LINE} tickLine={AXIS_LINE} />
              <Tooltip
                contentStyle={TOOLTIP_CONTENT} itemStyle={{ color: "#f5f2eb" }} labelStyle={{ color: "#a8aba8" }} cursor={{ fill: "rgba(255,255,255,0.04)" }}
              />
              <Legend wrapperStyle={LEGEND_STYLE} />
              {keys.map((key, index) => (
                <Bar
                  key={key}
                  dataKey={key}
                  fill={COLORS[index % COLORS.length]}
                  radius={[4, 4, 0, 0]}
                />
              ))}
            </BarChart>
          </ResponsiveContainer>
        );
    }
  };

  return (
    <Paper
      sx={{
        p: 2.5,
        borderRadius: "var(--radius)",
        backgroundColor: "var(--surface-2)",
        border: "1px solid var(--line)",
        boxShadow: "none",
      }}
    >
      <Box
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          mb: 2,
        }}
      >
        {title && (
          <Typography variant="h6" sx={{ fontWeight: 600, color: "var(--ink)", fontSize: "0.9375rem" }}>
            {title}
          </Typography>
        )}
        <ToggleButtonGroup
          value={chartType}
          exclusive
          onChange={handleChartTypeChange}
          size="small"
          sx={{ "& .MuiToggleButton-root": { px: 1.5, fontSize: "0.75rem" } }}
        >
          <ToggleButton value="bar">Bar</ToggleButton>
          <ToggleButton value="line">Line</ToggleButton>
          <ToggleButton value="area">Area</ToggleButton>
          <ToggleButton value="pie">Pie</ToggleButton>
        </ToggleButtonGroup>
      </Box>

      {renderChart()}
    </Paper>
  );
}
