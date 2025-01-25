import { useState, useEffect } from 'react';
import { useTheme } from "../components/theme-provider";
import { SidebarToggleButton } from "./SidebarToggleButton";
import { Sidebar } from "./Sidebar";
import { ChatContainer } from "./ChatContainer";
import { documentService } from '../services/documentService';

const AppLayout = () => {
    const { theme, setTheme } = useTheme();
    const [selectedDocument, setSelectedDocument] = useState(null);
    const [isSidebarOpen, setSidebarOpen] = useState(true);
    const [documents, setDocuments] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isSelectingDocument, setIsSelectingDocument] = useState(false);
    const [error, setError] = useState(null);
    const [activeDocumentId, setActiveDocumentId] = useState(
        localStorage.getItem('activeDocumentId')
    );

    // Fetch documents on component mount
    useEffect(() => {
        console.log('AppLayout mounted or re-mounted');
        fetchDocuments();
    }, []);

    // Restore selected document on mount and when documents change
    useEffect(() => {
        if (!isLoading && documents.length > 0) {
            const savedDocumentId = localStorage.getItem('selectedDocumentId');
            if (savedDocumentId) {
                const savedDocument = documents.find(doc => doc.id === savedDocumentId);
                if (savedDocument) {
                    setSelectedDocument(savedDocument);
                    // Ensure activeDocumentId is in sync
                    const savedActiveDocumentId = localStorage.getItem('activeDocumentId');
                    if (savedActiveDocumentId) {
                        setActiveDocumentId(savedActiveDocumentId);
                    }
                } else {
                    // Clean up localStorage if document no longer exists
                    localStorage.removeItem('selectedDocumentId');
                    localStorage.removeItem('activeDocumentId');
                    setActiveDocumentId(null);
                }
            }
        }
    }, [documents, isLoading]);

    // Save selected document and activeDocumentId to localStorage whenever they change
    useEffect(() => {
        if (selectedDocument) {
            localStorage.setItem('selectedDocumentId', selectedDocument.id);
            console.log('Saved selectedDocumentId to localStorage:', selectedDocument.id); // Debug log
        } else {
            localStorage.removeItem('selectedDocumentId');
            console.log('Removed selectedDocumentId from localStorage'); // Debug log
        }
    }, [selectedDocument]);

    const handleUploadComplete = () => {
        fetchDocuments();
    };

    const fetchDocuments = async () => {
        try {
            setIsLoading(true);
            setError(null);
            const data = await documentService.getDocuments();
            setDocuments(data);
        } catch (err) {
            setError('Failed to load documents');
            console.error('Error fetching documents:', err);
        } finally {
            setIsLoading(false);
        }
    };

    const handleDocumentSelect = async (doc) => {
        try {
            setError(null);
            setIsSelectingDocument(true);

            const response = await documentService.selectDocument(doc.id);
            setSelectedDocument(doc);
            setActiveDocumentId(response.activeDocument);

            // Update localStorage atomically
            localStorage.setItem('selectedDocumentId', doc.id);
            localStorage.setItem('activeDocumentId', response.activeDocument);

            // Close sidebar on mobile
            if (window.innerWidth < 1024) {
                setSidebarOpen(false);
            }
        } catch (err) {
            setError('Failed to select document');
            console.error('Error selecting document:', err);
        } finally {
            setIsSelectingDocument(false);
        }
    };

    const handleDocumentDelete = async (documentId) => {
        try {
            setError(null);
            await documentService.deleteDocument(documentId);

            setDocuments(prev => prev.filter(doc => doc.id !== documentId));

            // If deleted document was selected, clean up state and localStorage
            if (selectedDocument?.id === documentId) {
                setSelectedDocument(null);
                setActiveDocumentId(null);
                localStorage.removeItem('selectedDocumentId');
                localStorage.removeItem('activeDocumentId');

                // Also clean up chat messages for the deleted document
                localStorage.removeItem(`chatMessages_${documentId}`);
            }
        } catch (err) {
            setError('Failed to delete document');
            console.error('Error deleting document:', err);
        }
    };
    const toggleTheme = () => {
        setTheme(theme === 'dark' ? 'light' : 'dark');
    };

    const toggleSidebar = () => {
        setSidebarOpen(!isSidebarOpen);
    };

    return (
        <div className="flex h-screen bg-background overflow-hidden">
            <SidebarToggleButton isSidebarOpen={isSidebarOpen} toggleSidebar={toggleSidebar} />

            <Sidebar
                theme={theme}
                toggleTheme={toggleTheme}
                isSidebarOpen={isSidebarOpen}
                documents={documents}
                selectedDocument={selectedDocument}
                handleDocumentSelect={handleDocumentSelect}
                handleDocumentDelete={handleDocumentDelete}
                error={error}
                isLoading={isLoading}
                isSelectingDocument={isSelectingDocument}
                handleUploadComplete={handleUploadComplete}
            />

            {isSidebarOpen && (
                <div
                    className="fixed inset-0 bg-black/50 lg:hidden z-30"
                    onClick={() => setSidebarOpen(false)}
                />
            )}

            <ChatContainer
                selectedDocument={selectedDocument}
                isSelectingDocument={isSelectingDocument}
                activeDocumentId={activeDocumentId}
                key={`${selectedDocument?.id}-${activeDocumentId}`}
            />
        </div>
    );
};

export default AppLayout;