from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from typing import List, Optional
from sentence_transformers import SentenceTransformer
from mistralai import Mistral, UserMessage, SystemMessage
import numpy as np
import os
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

# Initialize FastAPI app
app = FastAPI()

# Initialize models and clients
embedding_model = SentenceTransformer('BAAI/bge-large-en-v1.5')
mistral_client = Mistral(api_key=os.getenv('MISTRAL_API_KEY'))

class EmbeddingRequest(BaseModel):
    texts: List[str]

class EmbeddingResponse(BaseModel):
    embeddings: List[List[float]]

class SummarizationRequest(BaseModel):
    query: str
    chunks: List[str]
    max_tokens: Optional[int] = Field(default=1024, gt=0)
    top_k: Optional[int] = Field(default=3, gt=0)

class InitialSummarizationRequest(BaseModel):
    chunks: List[str]
    max_tokens: Optional[int] = Field(default=1024, gt=0)
    chunk_count: Optional[int] = Field(default=5, gt=0)

class SummarizationResponse(BaseModel):
    summary: str
    ranked_chunks: List[str]
    chunk_scores: List[float]

class InitialSummarizationResponse(BaseModel):
    overview_summary: str
    key_topics: List[str]
    selected_chunks: List[str]

def rank_chunks(query: str, chunks: List[str], top_k: int = 3) -> tuple[List[str], List[float]]:
    """
    Rank chunks using BGE (Bidirectional Generative Encoder) embeddings for better semantic search.
    
    Args:
        query (str): The input query to match against the chunks.
        chunks (List[str]): A list of text chunks to rank.
        top_k (int, optional): The number of top matching chunks to return. Default is 3.
    
    Returns:
        tuple[List[str], List[float]]: 
            - A list of the top_k most relevant chunks.
            - A list of their corresponding similarity scores.
    """
    try:
        # Format the query for embedding generation
        query_text = f"Represent this sentence for retrieving relevant passages: {query}"

        # Generate vector embeddings for the query and chunks
        query_embedding = embedding_model.encode(query_text, normalize_embeddings=True)
        chunk_embeddings = embedding_model.encode(chunks, normalize_embeddings=True)

        # Compute the similarity between the query and each chunk using dot product
        similarities = np.dot(chunk_embeddings, query_embedding)

        # Sort chunks by similarity score in descending order and get the top_k indices
        ranked_indices = similarities.argsort()[::-1][:top_k]

        # Select the top_k chunks and their corresponding scores
        ranked_chunks = [chunks[i] for i in ranked_indices]
        ranked_scores = similarities[ranked_indices].tolist()

        return ranked_chunks, ranked_scores
    except Exception as e:
        # Handle errors gracefully and return default top_k chunks with dummy scores
        print(f"Warning: Chunk ranking failed: {e}")
        return chunks[:top_k], [1.0] * min(len(chunks), top_k)


def select_representative_chunks(chunks: List[str], chunk_count: int) -> List[str]:
    """
    Select diverse and representative chunks using embeddings clustering.
    
    Args:
        chunks (List[str]): A list of text chunks to choose from.
        chunk_count (int): The number of representative chunks to select.
    
    Returns:
        List[str]: A list of the selected representative chunks.
    """
    try:
        # Encode all chunks
        chunk_embeddings = embedding_model.encode(chunks, normalize_embeddings=True)
        
        selected_indices = []
        current_vectors = []
        
        # Select first chunk (usually contains important introductory information)
        selected_indices.append(0)
        current_vectors.append(chunk_embeddings[0])
        
        # Select remaining chunks based on maximal marginal relevance
        while len(selected_indices) < min(chunk_count, len(chunks)):
            max_score = -1
            best_idx = -1
            
            # For each candidate chunk
            for i in range(len(chunks)):
                if i not in selected_indices:
                    # Calculate relevance (average similarity to all chunks)
                    relevance = np.mean(np.dot(chunk_embeddings, chunk_embeddings[i]))
                    
                    # Calculate diversity (negative of max similarity to selected chunks)
                    if current_vectors:
                        similarity_to_selected = np.max(np.dot(np.array(current_vectors), chunk_embeddings[i]))
                        diversity = -similarity_to_selected
                    else:
                        diversity = 0
                    
                    # Combined score with emphasis on diversity
                    score = 0.3 * relevance + 0.7 * diversity
                    
                    if score > max_score:
                        max_score = score
                        best_idx = i
            
            selected_indices.append(best_idx)
            current_vectors.append(chunk_embeddings[best_idx])
        
        return [chunks[i] for i in selected_indices]
    except Exception as e:
        print(f"Warning: Representative chunk selection failed: {e}")
        return chunks[:chunk_count]

def generate_mistral_summary(query: str, chunks: List[str], max_tokens: int) -> str:
    """
    Generate a query-focused summary using Mistral AI
    
    Args:
        query (str): The input query to summarize against the chunks.
        chunks (List[str]): A list of text chunks to be summarized.
    
    Returns:
        response (str): Summarized response combining users query and relevant chunks
    """
    try:
        context = "\n\n".join(chunks)
        
        system_prompt = """You are an expert summarizer. Your task is to create a concise, 
        coherent summary of the provided text that specifically addresses the user's query. 
        Focus on the most relevant information while maintaining accuracy."""
        
        user_prompt = f"""Query: {query}

        Text to summarize:
        {context}

        Generate a focused summary that answers the query. Ensure the summary is coherent and 
        directly addresses the query while maintaining factual accuracy. If the text doesn't 
        contain relevant information to answer the query, mention that explicitly."""

        messages = [
            SystemMessage(role="system", content=system_prompt),
            UserMessage(role="user", content=user_prompt)
        ]
        
        response = mistral_client.chat.complete(
            model="mistral-medium",
            messages=messages,
            max_tokens=max_tokens,
            temperature=0.3
        )
        
        return response.choices[0].message.content
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Error during summarization: {str(e)}"
        )

def generate_initial_summary(chunks: List[str], max_tokens: int) -> tuple[str, List[str]]:
    """
    Generate an initial overview summary and extract key topics
    
    Args:
        chunks (List[str]): List of initial chunks from the uploaded/selected document.
        max_tokens (int): The maximum number of tokens to generate in the completion.
    
    Returns:
        response (str): Semantic Summary of the chunks.
    """
    try:
        context = "\n\n".join(chunks)
        
        system_prompt = """You are an expert document analyzer and summarizer. Your task is to provide 
        a comprehensive overview of the document and identify its key topics."""
        
        user_prompt = f"""Analyze the following document excerpts and provide:
        1. A high-level overview that captures the main points and purpose of the document
        2. A list of key topics or themes discussed in the document

        Text to analyze:
        {context}

        Provide the overview and topics in a clear, structured format. Focus on giving readers 
        a strong understanding of what the document is about and what they can expect to learn from it."""

        messages = [
            SystemMessage(role="system", content=system_prompt),
            UserMessage(role="user", content=user_prompt)
        ]
        
        response = mistral_client.chat.complete(
            model="mistral-medium",
            messages=messages,
            max_tokens=max_tokens,
            temperature=0.3
        )

        # print(response)
        
        # Get the summary
        full_response = response.choices[0].message.content
        
        # Extract topics using another Mistral call for better formatting
        topic_prompt = f"""Based on this overview, list the key topics in a concise format:

        {full_response}

        Return only the topics, one per line, without numbers or bullets."""
        
        topic_messages = [
            UserMessage(role="user", content=topic_prompt)
        ]
        
        topic_response = mistral_client.chat.complete(
            model="mistral-medium",
            messages=topic_messages,
            max_tokens=200,
            temperature=0.1
        )
        
        # Split the response into topics
        topics = [topic.strip() for topic in topic_response.choices[0].message.content.split('\n') if topic.strip()]
        
        return full_response, topics
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Error during initial summarization: {str(e)}"
        )

@app.post("/generate-embeddings", response_model=EmbeddingResponse)
async def generate_embeddings(request: EmbeddingRequest):
    """
    Generate embeddings for input texts using the BGE (Bidirectional Generative Encoder) model.
    
    Args:
        request (EmbeddingRequest): Contains the list of input texts to encode.
    
    Returns:
        EmbeddingResponse: A response containing the generated embeddings.
    """
    try:
        # Generate embeddings for the input texts and normalize them
        embeddings = embedding_model.encode(request.texts, normalize_embeddings=True)
        
        # Return the embeddings as a list
        return EmbeddingResponse(embeddings=embeddings.tolist())
    except Exception as e:
        # Handle errors by raising an HTTPException with a 500 status code
        raise HTTPException(status_code=500, detail=f"Error generating embeddings: {str(e)}")


@app.post("/summarize", response_model=SummarizationResponse)
async def summarize(request: SummarizationRequest):
    """
    Generate a query-focused summary of relevant chunks.
    
    Args:
        request (SummarizationRequest): Contains the query, chunks of text to summarize, 
                                        number of top chunks to consider, and max token limit.
    
    Returns:
        SummarizationResponse: Includes the generated summary, ranked chunks, and their scores.
    """
    try:
        # Rank chunks based on their relevance to the query
        ranked_chunks, chunk_scores = rank_chunks(
            request.query,
            request.chunks,
            request.top_k
        )
        
        # Generate a summary using the query and ranked chunks
        summary = generate_mistral_summary(
            request.query,
            ranked_chunks,
            request.max_tokens
        )
        
        # Return the summary, ranked chunks, and their scores
        return SummarizationResponse(
            summary=summary,
            ranked_chunks=ranked_chunks,
            chunk_scores=chunk_scores
        )
    except Exception as e:
        # Handle errors by raising an HTTPException with a 500 status code
        raise HTTPException(
            status_code=500,
            detail=f"Error during summarization: {str(e)}"
        )


@app.post("/initial-summary", response_model=InitialSummarizationResponse)
async def generate_initial_overview(request: InitialSummarizationRequest):
    """
    Generate an initial overview of a document.
    
    Args:
        request (InitialSummarizationRequest): Contains the document chunks, 
                                               number of representative chunks to select, 
                                               and the max token limit for the summary.
    
    Returns:
        InitialSummarizationResponse: Includes the overview summary, key topics, 
                                      and the selected representative chunks.
    """
    try:
        # Select representative and diverse chunks from the document
        selected_chunks = select_representative_chunks(
            request.chunks,
            request.chunk_count
        )
        
        # Generate a high-level summary and extract key topics from the selected chunks
        overview_summary, key_topics = generate_initial_summary(
            selected_chunks,
            request.max_tokens
        )
        
        # Return the summary, key topics, and selected chunks
        return InitialSummarizationResponse(
            overview_summary=overview_summary,
            key_topics=key_topics,
            selected_chunks=selected_chunks
        )
    except Exception as e:
        # Handle errors by raising an HTTPException with a 500 status code
        raise HTTPException(
            status_code=500,
            detail=f"Error during initial summarization: {str(e)}"
        )


@app.get("/")
async def read_root():
    return {"message": "Enhanced RAG Service is running!"}