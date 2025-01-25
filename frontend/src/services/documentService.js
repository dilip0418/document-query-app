import api from './api';

const DOCUMENTS_BASE_URL = '/documents';

export const documentService = {
    // Upload a new document
    uploadDocument: async (file, onUploadProgress) => {
        const formData = new FormData();
        formData.append('file', file);

        const response = await api.post(`${DOCUMENTS_BASE_URL}/upload`, formData, {
            headers: {
                'Content-Type': 'multipart/form-data',
            },
            onUploadProgress,
        });
        return response.data;
    },

    // Get list of all documents
    getDocuments: async () => {
        console.log(import.meta.env.VITE_BACKEND_BASE_URL);
        const response = await api.get(`${DOCUMENTS_BASE_URL}/list`);
        // console.log(response);
        return response.data;
    },

    // Select a document for chatting
    selectDocument: async (documentId) => {
        const response = await api.post(`/session/select-document/${documentId}`);
        return response.data;
    },

    deleteDocument: async (documentId) => {
        const response = await api.delete(`${DOCUMENTS_BASE_URL}/${documentId}`);
        print(response);
        return response.data;
    }
};