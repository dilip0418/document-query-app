import AppLayout from './components/AppLayout'
import { ThemeProvider } from "./components/theme-provider"

function App() {
  return (
    <ThemeProvider defaultTheme="system">
      <div className="min-h-screen bg-background">
        <AppLayout />
      </div>
    </ThemeProvider>
  )
}

export default App