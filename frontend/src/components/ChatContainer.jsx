/* eslint-disable react/prop-types */
import ChatInterface from './ChatInterface';

export const ChatContainer = ({ selectedDocument, isSelectingDocument, activeDocumentId }) => {
    return (
        <div className="flex-1 flex flex-col w-full">
            {isSelectingDocument ? (
                <div className="flex-1 flex items-center justify-center">
                    <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
                </div>
            ) : selectedDocument ? (
                <ChatInterface documentName={selectedDocument.name} activeDocumentId={activeDocumentId} />
            ) : (
                <div className="flex-1 flex items-center justify-center text-muted-foreground p-4">
                    <div className="text-center">
                        <h2 className="text-lg font-semibold mb-2">Welcome to DocQry 🙏</h2>
                        <p>Select a document from the sidebar to start chatting</p>
                    </div>
                </div>
            )}
        </div>
    );
};