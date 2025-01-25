/* eslint-disable no-unused-vars */
/* eslint-disable react/prop-types */
import { useState, useEffect } from 'react';
import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { Send, Loader2, User, Bot } from "lucide-react";
import { Avatar, AvatarImage, AvatarFallback } from "./ui/avatar";
import { chatService } from '../services';

const ChatInterface = ({ documentName, activeDocumentId }) => {
    const [messages, setMessages] = useState([]);
    const [input, setInput] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState(null);
    const [contextLimit, setContextLimit] = useState(4);

    // Load messages from localStorage when the component mounts or activeDocumentId changes
    useEffect(() => {
        if (activeDocumentId) {
            const savedMessages = localStorage.getItem(`chatMessages_${activeDocumentId}`);
            console.log(`Loading messages for activeDocumentId: ${activeDocumentId}`, savedMessages); // Debug log
            if (savedMessages) {
                try {
                    setMessages(JSON.parse(savedMessages));
                } catch (err) {
                    console.error('Error parsing messages from localStorage:', err);
                    setMessages([]); // Reset messages if parsing fails
                }
            }
        }
    }, [activeDocumentId]);

    // Save messages to localStorage whenever they change
    useEffect(() => {
        if (activeDocumentId) {
            console.log(`Saving messages for activeDocumentId: ${activeDocumentId}`, messages); // Debug log
            localStorage.setItem(`chatMessages_${activeDocumentId}`, JSON.stringify(messages));
        }
    }, [messages, activeDocumentId]);

    const handleSend = async () => {
        if (!input.trim() || isLoading) return;

        const userMessage = input.trim();
        setInput('');
        setError(null);

        // Add user message immediately
        const updatedMessages = [...messages, { role: 'user', content: userMessage }];
        setMessages(updatedMessages); // This triggers the useEffect that saves to localStorage

        try {
            setIsLoading(true);
            const response = await chatService.sendMessage(userMessage, contextLimit);

            // Add AI response
            setMessages(prev => [...prev, {
                role: 'assistant',
                content: response.llmResponse
            }]); // This also triggers the save to localStorage
        } catch (err) {
            setError('Failed to get response. Please try again.');
            console.error('Error getting LLM response:', err);
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <div className="flex flex-col h-full">
            {/* Header */}
            <div className="p-4 border-b flex justify-between items-center">
                <h2 className="text-lg font-semibold ml-10">
                    Chatting with: {documentName}
                </h2>
                <select
                    className="bg-background border rounded px-2 py-1 text-sm"
                    value={contextLimit}
                    onChange={(e) => setContextLimit(Number(e.target.value))}
                >
                    <option value={2}>2 chunks</option>
                    <option value={4}>4 chunks</option>
                    <option value={6}>6 chunks</option>
                    <option value={8}>8 chunks</option>
                </select>
            </div>

            {/* Messages Area */}
            <div className="flex-1 overflow-auto p-4 space-y-4">
                {messages.map((message, index) => (
                    <div
                        key={index}
                        className={`flex ${message.role === 'user' ? 'justify-end' : 'justify-start'} items-end gap-2`}
                    >
                        {/* Avatar for Assistant */}
                        {message.role === 'assistant' && (
                            <Avatar className="h-8 w-8">
                                <AvatarFallback>
                                    <Bot className="h-4 w-4" />
                                </AvatarFallback>
                            </Avatar>
                        )}

                        {/* Message Bubble */}
                        <div
                            className={`max-w-[80%] rounded-lg p-3 ${message.role === 'user'
                                ? 'bg-primary text-primary-foreground ml-4'
                                : 'bg-muted mr-4'
                                }`}
                        >
                            <p className="text-sm whitespace-pre-wrap">{message.content}</p>
                        </div>

                        {/* Avatar for User */}
                        {message.role === 'user' && (
                            <Avatar className="h-8 w-8">
                                <AvatarFallback>
                                    <User className="h-4 w-4" />
                                </AvatarFallback>
                            </Avatar>
                        )}
                    </div>
                ))}

                {error && (
                    <div className="text-sm text-red-500 text-center p-2 bg-red-50 dark:bg-red-900/10 rounded">
                        {error}
                    </div>
                )}
            </div>

            {/* Input Area */}
            <div className="border-t p-4">
                <div className="flex gap-2">
                    <Input
                        value={input}
                        onChange={(e) => setInput(e.target.value)}
                        placeholder="Ask something about the document..."
                        onKeyPress={(e) => e.key === 'Enter' && !isLoading && handleSend()}
                        disabled={isLoading}
                    />
                    <Button
                        onClick={handleSend}
                        disabled={isLoading}
                    >
                        {isLoading ? (
                            <Loader2 className="h-4 w-4 animate-spin" />
                        ) : (
                            <Send className="h-4 w-4" />
                        )}
                    </Button>
                </div>
            </div>
        </div>
    );
};

export default ChatInterface;