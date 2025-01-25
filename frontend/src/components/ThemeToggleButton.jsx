/* eslint-disable react/prop-types */
import { Button } from "../components/ui/button";
import { Moon, Sun } from "lucide-react";

export const ThemeToggleButton = ({ theme, toggleTheme }) => {
    return (
        <Button
            variant="outline"
            size="icon"
            onClick={toggleTheme}
        >
            {theme === 'dark' ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
        </Button>
    );
};