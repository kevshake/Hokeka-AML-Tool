import {
  Box,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
  Chip,
  Button,
  Tooltip,
} from "@mui/material";
import { useRoles } from "../../features/api/queries";

export default function RolesPage() {
  const { data: roles, isLoading } = useRoles();

  return (
    <Box>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 3 }}>
        <Typography variant="h5" sx={{ color: "text.primary", fontWeight: 600 }}>
          Role Management
        </Typography>
        <Tooltip title="Create a new role with custom permissions and access levels" arrow>
          <Button variant="contained" sx={{ backgroundColor: "#a93226", "&:hover": { backgroundColor: "#922b21" } }}>
            Create Role
          </Button>
        </Tooltip>
      </Box>

      <TableContainer component={Paper} sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell sx={{ color: "text.secondary" }}>Name</TableCell>
              <TableCell sx={{ color: "text.secondary" }}>Description</TableCell>
              <TableCell sx={{ color: "text.secondary" }}>Scope</TableCell>
              <TableCell sx={{ color: "text.secondary" }}>Permissions</TableCell>
              <TableCell sx={{ color: "text.secondary" }}>Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={5} align="center" sx={{ color: "text.disabled", py: 4 }}>
                  Loading roles...
                </TableCell>
              </TableRow>
            ) : roles && roles.length > 0 ? (
              roles.map((role) => (
                <TableRow key={role.id} hover>
                  <TableCell sx={{ color: "text.primary" }}>{role.name}</TableCell>
                  <TableCell sx={{ color: "text.primary" }}>
                    {role.description || "N/A"}
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={role.global ? "Global" : "PSP-Scoped"}
                      size="small"
                      sx={{
                        backgroundColor: role.global ? "#3498db20" : "#f39c1220",
                        color: role.global ? "#3498db" : "#f39c12",
                        border: `1px solid ${role.global ? "#3498db" : "#f39c12"}`,
                      }}
                    />
                  </TableCell>
                  <TableCell sx={{ color: "text.primary" }}>
                    {role.permissions?.length || 0} permissions
                  </TableCell>
                  <TableCell>
                    <Tooltip title="Edit role permissions and settings" arrow>
                      <Button size="small" sx={{ color: "#a93226", mr: 1 }}>
                        Edit
                      </Button>
                    </Tooltip>
                    <Tooltip title="Delete this role permanently" arrow>
                      <Button size="small" sx={{ color: "#e74c3c" }}>
                        Delete
                      </Button>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell colSpan={5} align="center" sx={{ color: "text.disabled", py: 4 }}>
                  No roles found
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  );
}

