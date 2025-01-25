/* eslint-disable react/prop-types */
import { useCallback, useState } from 'react';
import { Upload, X, FileText, AlertCircle, CheckCircle2 } from 'lucide-react';
import { useDropzone } from 'react-dropzone';
import {
    Card,
    CardContent,
    CardHeader,
    CardTitle,
} from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Progress } from '@/components/ui/progress';
import {
    Toast,
    ToastClose,
    ToastDescription,
    ToastProvider,
    ToastTitle,
    ToastViewport,
} from '@/components/ui/toast';
import { documentService } from '../services/documentService';

const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

const DocumentUpload = ({ onUploadComplete }) => {
    const [file, setFile] = useState(null);
    const [uploadProgress, setUploadProgress] = useState(0);
    const [uploading, setUploading] = useState(false);
    const [toast, setToast] = useState({ show: false, type: '', message: '' });

    const formatFileSize = (sizeInBytes) => `${(sizeInBytes / (1024 * 1024)).toFixed(1)} MB`;

    const showToast = (type, message) => {
        setToast({ show: true, type, message });
        setTimeout(() => setToast({ show: false, type: '', message: '' }), 3000);
    };

    const onDrop = useCallback((acceptedFiles) => {
        const file = acceptedFiles[0];

        if (!file) return;

        if (!file.name.endsWith('.txt')) {
            showToast('error', 'Only .txt files are allowed');
            return;
        }

        if (file.size > MAX_FILE_SIZE) {
            showToast('error', `File size must be less than ${formatFileSize(MAX_FILE_SIZE)}`);
            return;
        }

        setFile(file);
    }, []);

    const { getRootProps, getInputProps, isDragActive } = useDropzone({
        onDrop,
        accept: {
            'text/plain': ['.txt'],
        },
        maxFiles: 1,
    });

    const handleUpload = async () => {
        if (!file) return;

        setUploading(true);
        setUploadProgress(0);

        try {
            const response = await documentService.uploadDocument(file, (progressEvent) => {
                const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total);
                setUploadProgress(percentCompleted);
            });

            showToast('success', 'File uploaded successfully');
            setFile(null);
            setUploadProgress(100);

            onUploadComplete?.(response);
        } catch (error) {
            const errorMessage =
                error.response?.data?.message || 'An unexpected error occurred during file upload.';
            showToast('error', errorMessage);
        } finally {
            setUploading(false);
            setTimeout(() => setUploadProgress(0), 1000);
        }
    };

    const clearSelection = () => {
        setFile(null);
        setUploadProgress(0);
    };

    return (
        <div className="w-full max-w-xl mx-auto">
            <Card>
                <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                        <Upload className="w-5 h-5" />
                        Document Upload
                    </CardTitle>
                </CardHeader>
                <CardContent>
                    <div
                        {...getRootProps()}
                        className={`border-2 border-dashed rounded-lg p-6 text-center cursor-pointer transition-colors
              ${isDragActive ? 'border-blue-500 bg-blue-50' : 'border-gray-300'}
              ${file ? 'bg-gray-50' : ''}`}
                        aria-label="Upload your document by dragging and dropping or clicking to browse"
                        tabIndex={0}
                        role="button"
                    >
                        <input {...getInputProps()} />
                        {file ? (
                            <div className="flex items-center justify-center gap-2">
                                <FileText className="w-5 h-5 text-blue-500" />
                                <span className="font-medium dark:text-black">{file.name}</span>
                            </div>
                        ) : (
                            <div>
                                <Upload className="w-8 h-8 mx-auto text-gray-400 mb-2" />
                                <p className="text-gray-600">
                                    {isDragActive
                                        ? 'Drop the file here'
                                        : 'Drag and drop a .txt file here, or click to browse'}
                                </p>
                                <p className="text-sm text-gray-500 mt-1">
                                    Maximum file size: {formatFileSize(MAX_FILE_SIZE)}
                                </p>
                            </div>
                        )}
                    </div>

                    {file && (
                        <div className="mt-4 space-y-4">
                            {uploadProgress > 0 && (
                                <Progress value={uploadProgress} className="w-full" />
                            )}
                            <div className="flex gap-2">
                                <Button onClick={handleUpload} disabled={uploading} className="flex-1">
                                    {uploading ? 'Uploading...' : 'Upload'}
                                </Button>
                                <Button variant="outline" onClick={clearSelection} disabled={uploading}>
                                    <X className="w-4 h-4" />
                                </Button>
                            </div>
                        </div>
                    )}
                </CardContent>
            </Card>

            <ToastProvider>
                {toast.show && (
                    <Toast
                        variant={toast.type === 'error' ? 'destructive' : 'default'}
                        className="fixed bottom-4 right-4"
                    >
                        {toast.type === 'error' ? (
                            <AlertCircle className="w-4 h-4" />
                        ) : (
                            <CheckCircle2 className="w-4 h-4" />
                        )}
                        <ToastTitle>{toast.type === 'error' ? 'Error' : 'Success'}</ToastTitle>
                        <ToastDescription>{toast.message}</ToastDescription>
                        <ToastClose />
                    </Toast>
                )}
                <ToastViewport />
            </ToastProvider>
        </div>
    );
};

export default DocumentUpload;
