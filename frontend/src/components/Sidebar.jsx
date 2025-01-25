/* eslint-disable react/prop-types */
import { ThemeToggleButton } from "./ThemeToggleButton";
import DocumentUpload from "./DocumentUpload";
import { DocumentsList } from "./DocumentList";

export const Sidebar = ({
    theme,
    toggleTheme,
    isSidebarOpen,
    documents,
    selectedDocument,
    handleDocumentSelect,
    handleDocumentDelete,
    error,
    isLoading,
    isSelectingDocument,
    handleUploadComplete
}) => {
    return (
        <div className={`
        fixed lg:static
        inset-y-0 left-0
        w-80 border-r
        flex flex-col
        bg-background
        transform transition-transform duration-200 ease-in-out
        lg:transform-none
        ${isSidebarOpen ? 'translate-x-0' : '-translate-x-full'}
        z-40
      `}>
            <div className="p-4 border-b flex justify-between items-center">
                <h1 className="text-xl font-bold ml-10">Document Chat</h1>
                <ThemeToggleButton theme={theme} toggleTheme={toggleTheme} />
            </div>

            <DocumentUpload onUploadComplete={handleUploadComplete} />

            <DocumentsList
                documents={documents}
                selectedDocument={selectedDocument}
                handleDocumentSelect={handleDocumentSelect}
                handleDocumentDelete={handleDocumentDelete}
                isSelectingDocument={isSelectingDocument}
                error={error}
                isLoading={isLoading}
            />
        </div>
    );
};