/* eslint-disable react/prop-types */
import { useState } from "react";
import { Card } from "@/components/ui/card";
import { Trash } from "lucide-react";
import { DeleteConfirmationDialog } from "@/components/DeleteConfirmationDialog";
import { Button } from "./ui/button";

export const DocumentsList = ({
    documents,
    selectedDocument,
    handleDocumentSelect,
    handleDocumentDelete,
    error,
    isLoading,
    isSelectingDocument,
}) => {
    const [isDeleteDialogOpen, setIsDeleteDialogOpen] = useState(false);
    const [documentToDelete, setDocumentToDelete] = useState(null);

    const handleDeleteClick = (documentId) => {
        setDocumentToDelete(documentId);
        setIsDeleteDialogOpen(true);
    };

    const handleConfirmDelete = () => {
        if (documentToDelete) {
            handleDocumentDelete(documentToDelete);
            setIsDeleteDialogOpen(false);
            setDocumentToDelete(null);
        }
    };

    const handleCancelDelete = () => {
        setIsDeleteDialogOpen(false);
        setDocumentToDelete(null);
    };

    return (
        <div className="flex-1 overflow-auto p-4">
            <h2 className="text-sm font-semibold mb-2">Your Documents</h2>

            {error && (
                <div className="text-sm text-red-500 mb-2 p-2 bg-red-50 dark:bg-red-900/10 rounded">
                    {error}
                </div>
            )}

            {isLoading ? (
                <div className="flex justify-center items-center h-32">
                    <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
                </div>
            ) : (
                <div className="space-y-2">
                    {documents.map((doc) => (
                        <Card
                            key={doc.id}
                            className={`p-3 cursor-pointer hover:bg-accent ${selectedDocument?.id === doc.id ? "bg-accent" : ""
                                } ${isSelectingDocument ? "opacity-50 pointer-events-none" : ""}`}
                        >
                            <div className="flex justify-between items-center">
                                <div onClick={() => handleDocumentSelect(doc)}>
                                    <div className="text-sm font-medium">{doc.name}</div>
                                    <div className="text-xs text-muted-foreground">
                                        Uploaded: {new Date(doc.uploadDate).toLocaleDateString()}
                                    </div>
                                </div>
                                <Button
                                    variant="ghost"
                                    size="icon"
                                    onClick={(e) => {
                                        e.stopPropagation(); // Prevent card click event
                                        e.preventDefault();
                                        handleDeleteClick(doc.id);
                                    }}
                                    className="text-muted-foreground hover:text-red-500"
                                    disabled={isSelectingDocument}
                                >
                                    <Trash className="h-4 w-4" />
                                </Button>
                            </div>
                        </Card>
                    ))}
                    {documents.length === 0 && (
                        <div className="text-sm text-muted-foreground text-center py-4">
                            No documents found
                        </div>
                    )}
                </div>
            )}

            <DeleteConfirmationDialog
                isOpen={isDeleteDialogOpen}
                onClose={handleCancelDelete}
                onConfirm={handleConfirmDelete}
            />
        </div>
    );
};
