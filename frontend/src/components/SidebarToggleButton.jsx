/* eslint-disable react/prop-types */
import { Button } from "../components/ui/button";
import { Menu, X } from "lucide-react";

export const SidebarToggleButton = ({ isSidebarOpen, toggleSidebar }) => {
    return (
        <Button
            variant="outline"
            size="icon"
            className="fixed top-4 left-4 z-50 lg:hidden"
            onClick={toggleSidebar}
        >
            {isSidebarOpen ? <X className="h-4 w-4" /> : <Menu className="h-4 w-4" />}
        </Button>
    );
};