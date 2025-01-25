import api from './api';

const CHAT_BASE_URL = '/prompt';

export const chatService = {
    // Send message and get LLM response
    sendMessage: async (message, limit = 4) => {
        const response = await api.post(`${CHAT_BASE_URL}/llm-response`, {
            queryText: message,
            limit: limit
        });
        return response.data;
    }
};